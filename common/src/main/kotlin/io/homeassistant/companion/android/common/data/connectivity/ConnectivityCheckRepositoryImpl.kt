package io.homeassistant.companion.android.common.data.connectivity

import io.homeassistant.companion.android.common.R as commonR
import java.net.URL
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber

private const val DEFAULT_HTTP_PORT = 80
private const val DEFAULT_HTTPS_PORT = 443
private const val HTTPS_PROTOCOL = "https"

/**
 * Default implementation of [ConnectivityCheckRepository] that runs checks in sequence.
 */
class ConnectivityCheckRepositoryImpl @Inject constructor(private val checker: ConnectivityChecker) :
    ConnectivityCheckRepository {

    override fun runChecks(url: String): Flow<ConnectivityCheckState> = flow {
        var state = ConnectivityCheckState(serverUrl = url)
        emit(state)

        val parsedUrl = try {
            URL(url)
        } catch (e: Exception) {
            Timber.w(e, "Invalid URL format: $url")
            state = state.copy(
                dnsResolution = ConnectivityCheckResult.Failure(commonR.string.connection_check_error_invalid_url),
            )
            emit(state)
            return@flow
        }

        val hostname = parsedUrl.host
        val port = determinePort(parsedUrl)
        val isHttps = parsedUrl.protocol.equals(HTTPS_PROTOCOL, ignoreCase = true)

        // DNS Check
        state = state.copy(dnsResolution = ConnectivityCheckResult.InProgress)
        emit(state)
        val dnsResult = checker.dns(hostname)
        state = state.copy(dnsResolution = dnsResult)
        emit(state)

        if (dnsResult is ConnectivityCheckResult.Failure) {
            // Skip remaining checks if DNS fails
            val skipped = ConnectivityCheckResult.Failure(commonR.string.connection_check_skipped)
            state = state.copy(
                portReachability = skipped,
                tlsCertificate = skipped,
                serverConnection = skipped,
                homeAssistantVerification = skipped,
            )
            emit(state)
            return@flow
        }

        // Port Check
        state = state.copy(portReachability = ConnectivityCheckResult.InProgress)
        emit(state)
        val portResult = checker.port(hostname, port)
        state = state.copy(portReachability = portResult)
        emit(state)

        // TLS Check (bypass for HTTP)
        state = state.copy(tlsCertificate = ConnectivityCheckResult.InProgress)
        emit(state)
        val tlsResult = if (isHttps) checker.tls(url) else ConnectivityCheckResult.Success()
        state = state.copy(tlsCertificate = tlsResult)
        emit(state)

        // Server Connection Check
        state = state.copy(serverConnection = ConnectivityCheckResult.InProgress)
        emit(state)
        val serverResult = checker.server(url)
        state = state.copy(serverConnection = serverResult)
        emit(state)

        // Home Assistant Verification Check
        if (serverResult is ConnectivityCheckResult.Failure) {
            // Skip HA verification if server connection failed
            val skipped = ConnectivityCheckResult.Failure(commonR.string.connection_check_skipped)
            state = state.copy(homeAssistantVerification = skipped)
            emit(state)
            return@flow
        }

        state = state.copy(homeAssistantVerification = ConnectivityCheckResult.InProgress)
        emit(state)
        val haResult = checker.homeAssistant(url)
        state = state.copy(homeAssistantVerification = haResult)
        emit(state)
    }.flowOn(Dispatchers.IO)

    /**
     * Determines the port to use for connectivity checks.
     * Uses explicit port if provided, otherwise defaults based on protocol.
     */
    private fun determinePort(url: URL): Int = when {
        url.port != -1 -> url.port
        url.protocol.equals(HTTPS_PROTOCOL, ignoreCase = true) -> DEFAULT_HTTPS_PORT
        else -> DEFAULT_HTTP_PORT
    }
}
