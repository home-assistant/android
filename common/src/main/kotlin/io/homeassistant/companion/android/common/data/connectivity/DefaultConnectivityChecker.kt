package io.homeassistant.companion.android.common.data.connectivity

import io.homeassistant.companion.android.common.R as commonR
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import javax.inject.Inject
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.withTimeout
import timber.log.Timber

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
                ConnectivityCheckResult.Success(addressList)
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
                ConnectivityCheckResult.Success(port.toString())
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
                ConnectivityCheckResult.Success()
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
                ConnectivityCheckResult.Success()
            }
        } catch (e: Exception) {
            Timber.d(e, "Server connection failed for $url")
            ConnectivityCheckResult.Failure(commonR.string.connection_check_error_server)
        }
    }
}
