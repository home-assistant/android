package io.homeassistant.companion.android.common.data.connectivity

import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import io.homeassistant.companion.android.util.UrlUtil
import io.homeassistant.companion.android.util.sensitive
import java.net.InetAddress
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
internal class DefaultConnectivityChecker @Inject constructor(defaultOkHttpClient: OkHttpClient) : ConnectivityChecker {

    private val okHttpClient by lazy { configureOkHttpClientForChecker(defaultOkHttpClient) }

    override suspend fun dns(hostname: String): ConnectivityCheckResult = withContext(Dispatchers.IO) {
        try {
            withTimeout(CONNECTIVITY_TIMEOUT) {
                val addresses = InetAddress.getAllByName(hostname)
                val addressList = addresses.joinToString(", ") { it.hostAddress ?: "" }
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
            Socket().use { socket ->
                socket.connect(InetSocketAddress(hostname, port), CONNECTIVITY_TIMEOUT.inWholeMilliseconds.toInt())
            }
            ConnectivityCheckResult.Success(commonR.string.connection_check_port, port.toString())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.d(e, "Port $port not reachable on ${sensitive(hostname)}")
            ConnectivityCheckResult.Failure(commonR.string.connection_check_error_port)
        }
    }

    override suspend fun tls(url: String): ConnectivityCheckResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .head() // Don't get the body as we are only checking TLS
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                val handshake = response.handshake
                if (handshake != null) {
                    Timber.d("TLS check success for ${sensitive(url)} with ${handshake.tlsVersion}")
                    ConnectivityCheckResult.Success(commonR.string.connection_check_tls_success)
                } else {
                    Timber.d("Connection succeeded but no TLS handshake for ${sensitive(url)}")
                    ConnectivityCheckResult.Failure(commonR.string.connection_check_error_tls)
                }
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
            val request = Request.Builder()
                .url(url)
                .build()
            okHttpClient.newCall(request).execute().use {
                ConnectivityCheckResult.Success(commonR.string.connection_check_server_success)
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
            val request = Request.Builder()
                .url("${UrlUtil.extractBaseUrl(url)}manifest.json")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                val responseText = response.body.string()
                val manifest = kotlinJsonMapper.decodeFromString<ManifestResponse>(responseText)

                if (manifest.isHomeAssistant()) {
                    ConnectivityCheckResult.Success(commonR.string.connection_check_home_assistant_success)
                } else {
                    Timber.d("Manifest name mismatch: ${manifest.name}")
                    ConnectivityCheckResult.Failure(commonR.string.connection_check_error_not_home_assistant)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.d(e, "Home Assistant verification failed for ${sensitive(url)}")
            ConnectivityCheckResult.Failure(commonR.string.connection_check_error_not_home_assistant)
        }
    }

    /**
     * Preconfigures the provided [OkHttpClient] with timeouts for connectivity testing.
     * */
    private fun configureOkHttpClientForChecker(client: OkHttpClient): OkHttpClient = client.newBuilder()
        .connectTimeout(CONNECTIVITY_TIMEOUT)
        .readTimeout(CONNECTIVITY_TIMEOUT)
        .build()
}
