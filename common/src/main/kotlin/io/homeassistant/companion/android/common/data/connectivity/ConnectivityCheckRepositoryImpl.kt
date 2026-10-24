package io.homeassistant.companion.android.common.data.connectivity

import io.homeassistant.companion.android.common.R as commonR
import java.net.URL
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import timber.log.Timber

private const val DEFAULT_HTTP_PORT = 80
private const val DEFAULT_HTTPS_PORT = 443
private const val HTTPS_PROTOCOL = "https"

private data class ConnectionUrl(val hostname: String, val port: Int, val isHttps: Boolean)

/**
 * Default implementation of [ConnectivityCheckRepository] that runs checks in sequence.
 */
internal class ConnectivityCheckRepositoryImpl @Inject constructor(private val checker: ConnectivityChecker) :
    ConnectivityCheckRepository {

    override fun runChecks(url: String): Flow<ConnectivityCheckState> = flow {
        var state = ConnectivityCheckState()
        emit(state)

        // Parse url
        val connection = parseUrlOrEmitInvalid(url, state).getOrElse { return@flow }
        val hostname = connection.hostname
        val port = connection.port
        val isHttps = connection.isHttps

        // DNS Check
        state = dnsCheckEmitSkipIfFailed(state, hostname)
        if (state.dnsResolution is ConnectivityCheckResult.Failure) return@flow

        // Port Check
        state = portCheckAndEmit(state, hostname, port)

        // TLS Check
        state = tlsCheckOrEmitNotApplicable(state, isHttps, url)

        // Server Connection and Home Assistant Verification Checks, both answered by the manifest request
        state = manifestCheckAndEmit(state, url)
    }

    /**
     * Executes a single connectivity check with proper state transitions.
     * Emits InProgress state, runs the check, then emits the result state.
     *
     * @param currentState The current state before this check
     * @param setInProgress Function to update the state to InProgress for this check
     * @param setResult Function to update the state with the check result
     * @param check The suspend function that performs the actual connectivity check
     * @return The updated state after the check completes
     */
    private suspend fun <T> FlowCollector<ConnectivityCheckState>.runCheck(
        currentState: ConnectivityCheckState,
        setInProgress: (ConnectivityCheckState) -> ConnectivityCheckState,
        setResult: (ConnectivityCheckState, T) -> ConnectivityCheckState,
        check: suspend () -> T,
    ): ConnectivityCheckState {
        val inProgressState = setInProgress(currentState)
        emit(inProgressState)
        val result = check()
        val resultState = setResult(inProgressState, result)
        emit(resultState)
        return resultState
    }

    private suspend fun FlowCollector<ConnectivityCheckState>.parseUrlOrEmitInvalid(
        url: String,
        state: ConnectivityCheckState,
    ): Result<ConnectionUrl> = runCatching { URL(url) }
        .map { parsedUrl ->
            ConnectionUrl(
                hostname = parsedUrl.host,
                port = determinePort(parsedUrl),
                isHttps = parsedUrl.protocol.equals(HTTPS_PROTOCOL, ignoreCase = true),
            )
        }
        .onFailure { e ->
            Timber.w(e, "Invalid URL format: $url")
            emit(
                state.copy(
                    dnsResolution = ConnectivityCheckResult.Failure(
                        commonR.string.connection_check_error_invalid_url,
                    ),
                ).skipChecksAfterDns(),
            )
        }

    private suspend fun FlowCollector<ConnectivityCheckState>.dnsCheckEmitSkipIfFailed(
        state: ConnectivityCheckState,
        hostname: String,
    ): ConnectivityCheckState = runCheck(
        currentState = state,
        setInProgress = { it.copy(dnsResolution = ConnectivityCheckResult.InProgress) },
        setResult = { s, r -> s.copy(dnsResolution = r) },
        check = { checker.dns(hostname) },
    ).let { updated ->
        updated.takeUnless { it.dnsResolution is ConnectivityCheckResult.Failure }
            ?: updated.skipChecksAfterDns().also { emit(it) }
    }

    private suspend fun FlowCollector<ConnectivityCheckState>.portCheckAndEmit(
        state: ConnectivityCheckState,
        hostname: String,
        port: Int,
    ): ConnectivityCheckState = runCheck(
        currentState = state,
        setInProgress = { it.copy(portReachability = ConnectivityCheckResult.InProgress) },
        setResult = { s, r -> s.copy(portReachability = r) },
        check = { checker.port(hostname, port) },
    )

    private suspend fun FlowCollector<ConnectivityCheckState>.tlsCheckOrEmitNotApplicable(
        state: ConnectivityCheckState,
        isHttps: Boolean,
        url: String,
    ): ConnectivityCheckState = state.takeIf { isHttps }?.let {
        runCheck(
            currentState = it,
            setInProgress = { s -> s.copy(tlsCertificate = ConnectivityCheckResult.InProgress) },
            setResult = { s, r -> s.copy(tlsCertificate = r) },
            check = { checker.tls(url) },
        )
    } ?: state.copy(
        tlsCertificate = ConnectivityCheckResult.NotApplicable(
            commonR.string.connection_check_tls_not_applicable,
        ),
    ).also { emit(it) }

    /**
     * Runs the single manifest request behind the server connection and the Home Assistant verification
     * checks, so the two can never contradict each other.
     */
    private suspend fun FlowCollector<ConnectivityCheckState>.manifestCheckAndEmit(
        state: ConnectivityCheckState,
        url: String,
    ): ConnectivityCheckState = runCheck(
        currentState = state,
        setInProgress = {
            it.copy(
                serverConnection = ConnectivityCheckResult.InProgress,
                homeAssistantVerification = ConnectivityCheckResult.InProgress,
            )
        },
        setResult = { s, r -> s.withManifestResult(r) },
        check = { checker.homeAssistant(url) },
    )

    private fun ConnectivityCheckState.withManifestResult(result: ManifestCheckResult): ConnectivityCheckState {
        val answered = ConnectivityCheckResult.Success(commonR.string.connection_check_server_success)
        return when (result) {
            is ManifestCheckResult.NotReached -> copy(
                serverConnection = result.failure,
                homeAssistantVerification = ConnectivityCheckResult.Failure(commonR.string.connection_check_skipped),
            )
            is ManifestCheckResult.NotVerified -> copy(
                serverConnection = answered,
                homeAssistantVerification = result.failure,
            )
            ManifestCheckResult.Verified -> copy(
                serverConnection = answered,
                homeAssistantVerification = ConnectivityCheckResult.Success(
                    commonR.string.connection_check_home_assistant_success,
                ),
            )
        }
    }

    /**
     * Marks every check that needs a resolved hostname as skipped. Only valid once the DNS resolution
     * has been settled as a failure, either because the URL is invalid or because the hostname does
     * not resolve.
     */
    private fun ConnectivityCheckState.skipChecksAfterDns(): ConnectivityCheckState {
        val skipped = ConnectivityCheckResult.Failure(commonR.string.connection_check_skipped)
        return copy(
            portReachability = skipped,
            tlsCertificate = skipped,
            serverConnection = skipped,
            homeAssistantVerification = skipped,
        )
    }

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
