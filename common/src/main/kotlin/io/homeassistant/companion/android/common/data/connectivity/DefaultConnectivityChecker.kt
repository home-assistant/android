package io.homeassistant.companion.android.common.data.connectivity

import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import java.io.BufferedReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import javax.inject.Inject
import javax.net.ssl.HttpsURLConnection
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

/**
 * Default implementation of [ConnectivityChecker] that performs real network operations.
 *
 */
class DefaultConnectivityChecker @Inject constructor() : ConnectivityChecker {

    private val timeoutMs: Long = 5000L

    override suspend fun dns(hostname: String): ConnectivityCheckResult {
        return try {
            withTimeout(timeoutMs) {
                val addresses = InetAddress.getAllByName(hostname)
                val addressList = addresses.joinToString(", ") { it.hostAddress ?: "" }
                ConnectivityCheckResult.Success(commonR.string.connection_check_dns, addressList)
            }
        } catch (e: Exception) {
            Timber.d(e, "DNS resolution failed for $hostname")
            ConnectivityCheckResult.Failure(commonR.string.connection_check_error_dns)
        }
    }

    override suspend fun port(hostname: String, port: Int): ConnectivityCheckResult {
        return try {
            withTimeout(timeoutMs) {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(hostname, port), timeoutMs.toInt())
                }
                ConnectivityCheckResult.Success(commonR.string.connection_check_port, port.toString())
            }
        } catch (e: Exception) {
            Timber.d(e, "Port $port not reachable on $hostname")
            ConnectivityCheckResult.Failure(commonR.string.connection_check_error_port)
        }
    }

    override suspend fun tls(url: String): ConnectivityCheckResult {
        return try {
            withTimeout(timeoutMs) {
                val connection = URL(url).openConnection() as HttpsURLConnection
                connection.connectTimeout = timeoutMs.toInt()
                connection.connect()
                connection.disconnect()
                ConnectivityCheckResult.Success(commonR.string.connection_check_tls_success)
            }
        } catch (e: Exception) {
            Timber.d(e, "TLS check failed for $url")
            ConnectivityCheckResult.Failure(commonR.string.connection_check_error_tls)
        }
    }

    override suspend fun server(url: String): ConnectivityCheckResult {
        return try {
            withTimeout(timeoutMs) {
                val connection = URL(url).openConnection()
                connection.connectTimeout = timeoutMs.toInt()
                connection.connect()
                ConnectivityCheckResult.Success(commonR.string.connection_check_server_success)
            }
        } catch (e: Exception) {
            Timber.d(e, "Server connection failed for $url")
            ConnectivityCheckResult.Failure(commonR.string.connection_check_error_server)
        }
    }

    override suspend fun homeAssistant(url: String): ConnectivityCheckResult {
        return try {
            withTimeout(timeoutMs) {
                val manifestUrl = URL("$url/manifest.json")
                val connection = manifestUrl.openConnection()
                connection.connectTimeout = timeoutMs.toInt()
                connection.readTimeout = timeoutMs.toInt()

                val responseText = connection.getInputStream().bufferedReader().use(BufferedReader::readText)
                val manifest = kotlinJsonMapper.decodeFromString<ManifestResponse>(responseText)

                if (manifest.isHomeAssistant()) {
                    ConnectivityCheckResult.Success(commonR.string.connection_check_home_assistant_success)
                } else {
                    Timber.d("Manifest name mismatch: ${manifest.name}")
                    ConnectivityCheckResult.Failure(commonR.string.connection_check_error_not_home_assistant)
                }
            }
        } catch (e: Exception) {
            Timber.d(e, "Home Assistant verification failed for $url")
            ConnectivityCheckResult.Failure(commonR.string.connection_check_error_not_home_assistant)
        }
    }
}
