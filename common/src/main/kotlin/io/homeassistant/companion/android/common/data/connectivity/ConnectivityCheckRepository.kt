package io.homeassistant.companion.android.common.data.connectivity

import kotlinx.coroutines.flow.Flow

/**
 * Repository for performing connectivity checks against a server URL.
 * Checks include DNS resolution, port reachability, TLS certificate validation, and server connection.
 */
interface ConnectivityCheckRepository {

    /**
     * Runs all connectivity checks for the given URL and emits state updates as each check completes.
     *
     * @param url The server URL to check connectivity for
     * @return A Flow emitting [ConnectivityCheckState] updates as checks progress.
     *         The Flow completes when all connectivity checks have finished.
     */
    fun runChecks(url: String): Flow<ConnectivityCheckState>
}
