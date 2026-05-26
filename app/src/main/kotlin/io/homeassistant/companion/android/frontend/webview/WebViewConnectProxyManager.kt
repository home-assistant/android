package io.homeassistant.companion.android.frontend.webview

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.data.network.LocalConnectProxy
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber

/**
 * Routes all WebView network traffic through [LocalConnectProxy] so DNS resolution uses
 * [io.homeassistant.companion.android.common.data.network.NetworkAwareDns].
 *
 * TLS and WebSocket still target the original hostname, preserving certificate validation.
 */
@Singleton
class WebViewConnectProxyManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localConnectProxy: LocalConnectProxy,
) {
    private var configuredPort: Int? = null

    /** Returns whether WebView traffic is currently routed through the local CONNECT proxy. */
    fun isActive(): Boolean = configuredPort != null

    /** Returns whether the installed WebView supports [WebViewFeature.PROXY_OVERRIDE]. */
    fun isProxyOverrideSupported(): Boolean =
        WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)

    /**
     * Starts the local CONNECT proxy and configures WebView to use it when supported.
     *
     * @return `true` when WebView traffic is routed through the proxy, `false` when
     *   [androidx.webkit.WebViewFeature.PROXY_OVERRIDE] is unavailable on this device.
     */
    suspend fun ensureConfigured(): Boolean {
        if (!isProxyOverrideSupported()) {
            Timber.tag(TAG).w("WebView PROXY_OVERRIDE is not supported on this device")
            return false
        }
        configuredPort?.let { return true }

        val port = localConnectProxy.start() ?: return false
        val proxyConfig = ProxyConfig.Builder()
            .addProxyRule("127.0.0.1:$port", ProxyConfig.MATCH_ALL_SCHEMES)
            .build()

        return suspendCancellableCoroutine { continuation ->
            ProxyController.getInstance().setProxyOverride(
                proxyConfig,
                ContextCompat.getMainExecutor(context),
            ) {
                configuredPort = port
                Timber.tag(TAG).d("WebView proxy configured via 127.0.0.1:%d", port)
                continuation.resume(true)
            }
        }
    }

    /** Clears the WebView proxy override and stops the local CONNECT proxy. */
    fun clear() {
        configuredPort = null
        localConnectProxy.stop()
        if (!isProxyOverrideSupported()) {
            return
        }
        ProxyController.getInstance().clearProxyOverride(ContextCompat.getMainExecutor(context)) {
            Timber.tag(TAG).d("WebView proxy cleared")
        }
    }

    private companion object {
        private const val TAG = "WebViewConnectProxy"
    }
}
