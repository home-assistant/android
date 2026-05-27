package io.homeassistant.companion.android.frontend.webview

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.data.network.LocalConnectProxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber

/**
 * Routes all WebView network traffic through [LocalConnectProxy] so DNS resolution uses
 * [io.homeassistant.companion.android.common.data.network.NetworkAwareDns].
 *
 * TLS and WebSocket still target the original hostname, preserving certificate validation.
 *
 * Call [retainSession] after a successful [ensureConfigured] and [releaseSession] when the
 * WebView consumer is destroyed so the proxy is stopped when no sessions remain.
 */
@Singleton
class WebViewConnectProxyManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localConnectProxy: LocalConnectProxy,
) {
    private val portLock = Any()
    private val sessionLock = Any()
    private val activeSessions = AtomicInteger(0)
    private val configureGeneration = AtomicInteger(0)
    private val clearGeneration = AtomicInteger(0)
    private val proxyExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "WebViewConnectProxy-ops").apply { isDaemon = true }
    }
    private val proxyDispatcher = proxyExecutor.asCoroutineDispatcher()
    private var configuredPort: Int? = null

    /** Returns whether WebView traffic is currently routed through the local CONNECT proxy. */
    fun isActive(): Boolean = synchronized(portLock) { configuredPort != null }

    /** Returns whether the installed WebView supports [WebViewFeature.PROXY_OVERRIDE]. */
    fun isProxyOverrideSupported(): Boolean = WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)

    /**
     * Registers an active WebView session that depends on the CONNECT proxy.
     *
     * Invoke once per consumer after [ensureConfigured] returns `true`.
     */
    fun retainSession() {
        synchronized(sessionLock) {
            activeSessions.incrementAndGet()
        }
    }

    /**
     * Unregisters a WebView session; stops the proxy when the last session is released.
     */
    fun releaseSession() {
        val shouldClear = synchronized(sessionLock) {
            when (val remaining = activeSessions.decrementAndGet()) {
                0 -> true
                in 1..Int.MAX_VALUE -> false
                else -> {
                    Timber.tag(TAG).w("releaseSession called without matching retainSession")
                    activeSessions.set(0)
                    false
                }
            }
        }
        if (shouldClear) {
            clearInternal(resetSessions = false)
        }
    }

    /**
     * Starts the local CONNECT proxy and configures WebView to use it when supported.
     *
     * Re-applies [ProxyController.setProxyOverride] even when already configured so a concurrent
     * clear cannot leave an active session without a proxy override.
     *
     * @return `true` when WebView traffic is routed through the proxy, `false` when
     *   [androidx.webkit.WebViewFeature.PROXY_OVERRIDE] is unavailable on this device or
     *   configuration timed out.
     */
    suspend fun ensureConfigured(): Boolean = withContext(proxyDispatcher) {
        if (!isProxyOverrideSupported()) {
            Timber.tag(TAG).w("WebView PROXY_OVERRIDE is not supported on this device")
            return@withContext false
        }

        val port = synchronized(portLock) {
            configuredPort ?: localConnectProxy.start()
        } ?: return@withContext false

        configureProxyOverride(port)
    }

    private suspend fun configureProxyOverride(port: Int): Boolean {
        val generation = configureGeneration.incrementAndGet()

        return try {
            withTimeout(PROXY_OVERRIDE_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    val proxyConfig = ProxyConfig.Builder()
                        .addProxyRule("127.0.0.1:$port", ProxyConfig.MATCH_ALL_SCHEMES)
                        .build()

                    ProxyController.getInstance().setProxyOverride(
                        proxyConfig,
                        ContextCompat.getMainExecutor(context),
                    ) {
                        if (configureGeneration.get() != generation) {
                            Timber.tag(TAG).d("Ignoring stale WebView proxy configure callback")
                            stopProxyIfUnused(expectedPort = port)
                            return@setProxyOverride
                        }
                        synchronized(portLock) {
                            configuredPort = port
                        }
                        Timber.tag(TAG).d("WebView proxy configured via 127.0.0.1:%d", port)
                        if (continuation.isActive) {
                            continuation.resume(true)
                        }
                    }
                }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            configureGeneration.incrementAndGet()
            Timber.tag(TAG).w(exception, "WebView proxy configuration timed out or failed")
            stopProxyIfUnused(expectedPort = port)
            false
        }
    }

    /** Clears the WebView proxy override and stops the local CONNECT proxy. */
    fun clear() {
        clearInternal(resetSessions = true)
    }

    private fun clearInternal(resetSessions: Boolean) {
        configureGeneration.incrementAndGet()
        val generation = clearGeneration.incrementAndGet()
        if (resetSessions) {
            synchronized(sessionLock) {
                activeSessions.set(0)
            }
        }

        proxyExecutor.execute {
            if (clearGeneration.get() != generation) {
                Timber.tag(TAG).d("Skipping superseded WebView proxy clear")
                return@execute
            }
            if (activeSessions.get() > 0) {
                Timber.tag(TAG).d("Skipping WebView proxy clear because a session is still active")
                return@execute
            }

            if (!isProxyOverrideSupported()) {
                synchronized(portLock) { configuredPort = null }
                localConnectProxy.stop()
                return@execute
            }

            val portToStop = synchronized(portLock) {
                if (activeSessions.get() > 0) {
                    return@execute
                }
                configuredPort?.also { configuredPort = null }
            } ?: return@execute

            val latch = CountDownLatch(1)
            ProxyController.getInstance().clearProxyOverride(ContextCompat.getMainExecutor(context)) {
                Timber.tag(TAG).d("WebView proxy cleared")
                latch.countDown()
            }
            if (!latch.await(PROXY_OVERRIDE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                Timber.tag(TAG).w("WebView proxy clear timed out")
            }
            if (activeSessions.get() > 0) {
                Timber.tag(TAG).d(
                    "Session became active during proxy clear; keeping CONNECT proxy running",
                )
                return@execute
            }
            if (portToStop != null && localConnectProxy.currentPort() == portToStop) {
                localConnectProxy.stop()
            }
        }
    }

    private fun stopProxyIfUnused(expectedPort: Int) {
        val shouldStop = synchronized(portLock) {
            configuredPort == null
        }
        if (shouldStop && localConnectProxy.currentPort() == expectedPort) {
            localConnectProxy.stop()
        }
    }

    private companion object {
        private const val TAG = "WebViewConnectProxy"
        private const val PROXY_OVERRIDE_TIMEOUT_MS = 10_000L
    }
}
