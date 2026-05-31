package io.homeassistant.companion.android.common.data.connectivity

import androidx.annotation.StringRes
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.network.NetworkAwareDns
import io.homeassistant.companion.android.common.data.network.openSocketOnNetwork
import io.homeassistant.companion.android.common.data.network.orderConnectAddresses
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import io.homeassistant.companion.android.util.UrlUtil
import io.homeassistant.companion.android.util.sensitive
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber

@Serializable
private data class ManifestResponse(val name: String? = null) {
    fun isHomeAssistant(): Boolean = name == HOME_ASSISTANT_NAME

    companion object {
        private const val HOME_ASSISTANT_NAME = "Home Assistant"
    }
}

private val CONNECTIVITY_TIMEOUT = 5.seconds
private val PORT_ATTEMPT_TIMEOUT = 2.seconds

/**
 * Default implementation of [ConnectivityChecker] that performs real network operations.
 */
internal class DefaultConnectivityChecker @Inject constructor(
    private val networkAwareDns: NetworkAwareDns,
    private val okHttpClient: OkHttpClient,
) : ConnectivityChecker {

    override suspend fun dns(hostname: String): ConnectivityCheckResult = withContext(Dispatchers.IO) {
        try {
            withTimeout(CONNECTIVITY_TIMEOUT) {
                val boundLookup = networkAwareDns.lookupBoundToActiveNetwork(hostname)
                val addressList = boundLookup.addresses.joinToString(", ") { it.hostAddress ?: "" }
                ConnectivityCheckResult.Success(commonR.string.connection_check_dns, addressList)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.d(e, "DNS resolution failed for ${sensitive(hostname)}")
            ConnectivityCheckResult.Failure(commonR.string.connection_check_error_dns)
        }
    }

    override suspend fun port(hostname: String, port: Int): ConnectivityCheckResult = withContext(Dispatchers.IO) {
        try {
            withTimeout(CONNECTIVITY_TIMEOUT) {
                val boundLookup = networkAwareDns.lookupBoundToActiveNetwork(hostname)
                val addresses = orderConnectAddresses(boundLookup.addresses)
                var lastException: Exception? = null
                for (address in addresses) {
                    try {
                        withTimeout(PORT_ATTEMPT_TIMEOUT) {
                            openSocketOnNetwork(
                                network = boundLookup.network,
                                address = address,
                                port = port,
                                connectTimeoutMs = PORT_ATTEMPT_TIMEOUT.inWholeMilliseconds.toInt(),
                            ).use { /* connected */ }
                        }
                        return@withTimeout ConnectivityCheckResult.Success(
                            commonR.string.connection_check_port,
                            port.toString(),
                        )
                    } catch (e: Exception) {
                        lastException = e
                        Timber.d(
                            e,
                            "Port %d not reachable on %s via %s",
                            port,
                            sensitive(hostname),
                            sensitive(address.hostAddress ?: address.toString()),
                        )
                    }
                }
                throw lastException ?: IOException("No addresses resolved for ${sensitive(hostname)}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.d(e, "Port $port not reachable on ${sensitive(hostname)}")
            ConnectivityCheckResult.Failure(commonR.string.connection_check_error_port)
        }
    }

    override suspend fun tls(url: String): ConnectivityCheckResult = withContext(Dispatchers.IO) {
        try {
            withTimeout(CONNECTIVITY_TIMEOUT) {
                executeHeadRequest(url, commonR.string.connection_check_tls_success)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.d(e, "TLS check failed for ${sensitive(url)}")
            ConnectivityCheckResult.Failure(commonR.string.connection_check_error_tls)
        }
    }

    override suspend fun server(url: String): ConnectivityCheckResult = withContext(Dispatchers.IO) {
        try {
            withTimeout(CONNECTIVITY_TIMEOUT) {
                executeHeadRequest(url, commonR.string.connection_check_server_success)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.d(e, "Server connection failed for ${sensitive(url)}")
            ConnectivityCheckResult.Failure(commonR.string.connection_check_error_server)
        }
    }

    override suspend fun homeAssistant(url: String): ConnectivityCheckResult = withContext(Dispatchers.IO) {
        try {
            withTimeout(CONNECTIVITY_TIMEOUT) {
                val manifestUrl = "${UrlUtil.extractBaseUrl(url)}manifest.json"
                val request = Request.Builder().url(manifestUrl).get().build()
                executeRequest(request) { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}")
                    }
                    val responseText = response.body?.string()
                        ?: throw IOException("Empty manifest response")
                    val manifest = kotlinJsonMapper.decodeFromString<ManifestResponse>(responseText)
                    if (manifest.isHomeAssistant()) {
                        ConnectivityCheckResult.Success(commonR.string.connection_check_home_assistant_success)
                    } else {
                        Timber.d("Manifest name mismatch: ${manifest.name}")
                        ConnectivityCheckResult.Failure(commonR.string.connection_check_error_not_home_assistant)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.d(e, "Home Assistant verification failed for ${sensitive(url)}")
            ConnectivityCheckResult.Failure(commonR.string.connection_check_error_not_home_assistant)
        }
    }

    private suspend fun executeHeadRequest(url: String, @StringRes successMessage: Int): ConnectivityCheckResult {
        val request = Request.Builder().url(url).head().build()
        return executeRequest(request) { response ->
            if (response.isSuccessful || response.code in 400..499) {
                ConnectivityCheckResult.Success(successMessage)
            } else {
                throw IOException("HTTP ${response.code}")
            }
        }
    }

    private suspend fun executeRequest(
        request: Request,
        transform: (Response) -> ConnectivityCheckResult,
    ): ConnectivityCheckResult = suspendCancellableCoroutine { continuation ->
        val call = okHttpClient.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, exception: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(exception)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        try {
                            val result = transform(response)
                            if (continuation.isActive) {
                                continuation.resume(result)
                            }
                        } catch (exception: Exception) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(exception)
                            }
                        }
                    }
                }
            },
        )
    }
}
