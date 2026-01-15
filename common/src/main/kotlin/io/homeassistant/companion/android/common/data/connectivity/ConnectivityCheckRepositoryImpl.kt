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

private enum class SkipReason {
    AFTER_DNS_FAILURE,
    AFTER_SERVER_FAILURE,
    INVALID_URL,
}

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

        // Server Connection Check
        state = serverCheckEmitSkipIfFailed(state, url)
        if (state.serverConnection is ConnectivityCheckResult.Failure) return@flow

        // Home Assistant Verification Check
        state = homeAssistantCheckAndEmit(state, url)
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
    private suspend fun FlowCollector<ConnectivityCheckState>.runCheck(
        currentState: ConnectivityCheckState,
        setInProgress: (ConnectivityCheckState) -> ConnectivityCheckState,
        setResult: (ConnectivityCheckState, ConnectivityCheckResult) -> ConnectivityCheckState,
        check: suspend () -> ConnectivityCheckResult,
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
                ).skip(SkipReason.INVALID_URL),
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
            ?: updated.skip(SkipReason.AFTER_DNS_FAILURE).also { emit(it) }
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

    private suspend fun FlowCollector<ConnectivityCheckState>.serverCheckEmitSkipIfFailed(
        state: ConnectivityCheckState,
        url: String,
    ): ConnectivityCheckState = runCheck(
        currentState = state,
        setInProgress = { it.copy(serverConnection = ConnectivityCheckResult.InProgress) },
        setResult = { s, r -> s.copy(serverConnection = r) },
        check = { checker.server(url) },
    ).let { updated ->
        updated.takeUnless { it.serverConnection is ConnectivityCheckResult.Failure }
            ?: updated.skip(SkipReason.AFTER_SERVER_FAILURE).also { emit(it) }
    }

    private suspend fun FlowCollector<ConnectivityCheckState>.homeAssistantCheckAndEmit(
        state: ConnectivityCheckState,
        url: String,
    ): ConnectivityCheckState = runCheck(
        currentState = state,
        setInProgress = { it.copy(homeAssistantVerification = ConnectivityCheckResult.InProgress) },
        setResult = { s, r -> s.copy(homeAssistantVerification = r) },
        check = { checker.homeAssistant(url) },
    )

    /**
     * Marks remaining connectivity checks as skipped based on which check failed.
     *
     * - [SkipReason.AFTER_DNS_FAILURE]: Skips port, TLS, server, and Home Assistant checks.
     * - [SkipReason.AFTER_SERVER_FAILURE]: Skips only the Home Assistant verification check.
     */
    private fun ConnectivityCheckState.skip(reason: SkipReason): ConnectivityCheckState {
        val skipped = ConnectivityCheckResult.Failure(commonR.string.connection_check_skipped)
        return when (reason) {
            SkipReason.INVALID_URL,
            SkipReason.AFTER_DNS_FAILURE,
            -> copy(
                portReachability = skipped,
                tlsCertificate = skipped,
                serverConnection = skipped,
                homeAssistantVerification = skipped,
            )
            SkipReason.AFTER_SERVER_FAILURE -> copy(
                homeAssistantVerification = skipped,
            )
        }
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
