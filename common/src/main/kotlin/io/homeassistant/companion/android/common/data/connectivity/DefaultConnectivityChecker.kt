package io.homeassistant.companion.android.common.data.connectivity

import androidx.annotation.StringRes
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.network.NetworkAwareDns
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import io.homeassistant.companion.android.util.UrlUtil
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

@Serializable
private data class ManifestResponse(val name: String? = null) {
    fun isHomeAssistant(): Boolean = name == HOME_ASSISTANT_NAME

    companion object {
        private const val HOME_ASSISTANT_NAME = "Home Assistant"
    }
}

private val CONNECTIVITY_TIMEOUT = 5.seconds

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
                val addresses = networkAwareDns.lookup(hostname)
                val addressList = addresses.joinToString(", ") { it.hostAddress ?: "" }
                ConnectivityCheckResult.Success(commonR.string.connection_check_dns, addressList)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.d(e, "DNS resolution failed for $hostname")
            ConnectivityCheckResult.Failure(commonR.string.connection_check_error_dns)
        }
    }

    override suspend fun port(hostname: String, port: Int): ConnectivityCheckResult = withContext(Dispatchers.IO) {
        try {
            withTimeout(CONNECTIVITY_TIMEOUT) {
                val addresses = networkAwareDns.lookup(hostname)
                var lastException: Exception? = null
                for (address in addresses) {
                    try {
                        Socket().use { socket ->
                            socket.connect(
                                InetSocketAddress(address, port),
                                CONNECTIVITY_TIMEOUT.inWholeMilliseconds.toInt(),
                            )
                        }
                        return@withTimeout ConnectivityCheckResult.Success(
                            commonR.string.connection_check_port,
                            port.toString(),
                        )
                    } catch (e: Exception) {
                        lastException = e
                    }
                }
                throw lastException ?: IOException("No addresses resolved for $hostname")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.d(e, "Port $port not reachable on $hostname")
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
            Timber.d(e, "TLS check failed for $url")
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
            Timber.d(e, "Server connection failed for $url")
            ConnectivityCheckResult.Failure(commonR.string.connection_check_error_server)
        }
    }

    override suspend fun homeAssistant(url: String): ConnectivityCheckResult = withContext(Dispatchers.IO) {
        try {
            withTimeout(CONNECTIVITY_TIMEOUT) {
                val manifestUrl = "${UrlUtil.extractBaseUrl(url)}manifest.json"
                val request = Request.Builder().url(manifestUrl).get().build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}")
                    }
                    val responseText = response.body.string()
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
            Timber.d(e, "Home Assistant verification failed for $url")
            ConnectivityCheckResult.Failure(commonR.string.connection_check_error_not_home_assistant)
        }
    }

    private fun executeHeadRequest(url: String, @StringRes successMessage: Int): ConnectivityCheckResult {
        val request = Request.Builder().url(url).head().build()
        okHttpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful || response.code in 400..499) {
                return ConnectivityCheckResult.Success(successMessage)
            }
            throw IOException("HTTP ${response.code}")
        }
    }
}
