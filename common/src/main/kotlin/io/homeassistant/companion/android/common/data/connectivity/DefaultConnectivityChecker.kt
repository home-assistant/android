package io.homeassistant.companion.android.common.data.connectivity

import androidx.annotation.StringRes
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.net.URLConnection
import javax.inject.Inject
import javax.net.ssl.HttpsURLConnection
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
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
internal class DefaultConnectivityChecker @Inject constructor() : ConnectivityChecker {

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
            Timber.d(e, "DNS resolution failed for $hostname")
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
            Timber.d(e, "Port $port not reachable on $hostname")
            ConnectivityCheckResult.Failure(commonR.string.connection_check_error_port)
        }
    }

    override suspend fun tls(url: String): ConnectivityCheckResult = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection() as HttpsURLConnection
            connection.connectTimeout = CONNECTIVITY_TIMEOUT.inWholeMilliseconds.toInt()
            connection.readTimeout = CONNECTIVITY_TIMEOUT.inWholeMilliseconds.toInt()
            connection.tryConnect(commonR.string.connection_check_tls_success)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.d(e, "TLS check failed for $url")
            ConnectivityCheckResult.Failure(commonR.string.connection_check_error_tls)
        }
    }

    override suspend fun server(url: String): ConnectivityCheckResult = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection()
            connection.connectTimeout = CONNECTIVITY_TIMEOUT.inWholeMilliseconds.toInt()
            connection.readTimeout = CONNECTIVITY_TIMEOUT.inWholeMilliseconds.toInt()
            connection.tryConnect(commonR.string.connection_check_server_success)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.d(e, "Server connection failed for $url")
            ConnectivityCheckResult.Failure(commonR.string.connection_check_error_server)
        }
    }

    override suspend fun homeAssistant(url: String): ConnectivityCheckResult = withContext(Dispatchers.IO) {
        try {
            val manifestUrl = URL("$url/manifest.json")
            val connection = manifestUrl.openConnection()
            connection.connectTimeout = CONNECTIVITY_TIMEOUT.inWholeMilliseconds.toInt()
            connection.readTimeout = CONNECTIVITY_TIMEOUT.inWholeMilliseconds.toInt()

            connection.withConnection {
                val responseText = connection.getInputStream().bufferedReader().use(BufferedReader::readText)
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
            Timber.d(e, "Home Assistant verification failed for $url")
            ConnectivityCheckResult.Failure(commonR.string.connection_check_error_not_home_assistant)
        }
    }

    private fun URLConnection.tryConnect(@StringRes successMessage: Int): ConnectivityCheckResult {
        return withConnection {
            ConnectivityCheckResult.Success(successMessage)
        }
    }

    private inline fun <T> URLConnection.withConnection(block: () -> T): T {
        return try {
            connect()
            block()
        } finally {
            if (this is HttpURLConnection) {
                disconnect()
            }
        }
    }
}
