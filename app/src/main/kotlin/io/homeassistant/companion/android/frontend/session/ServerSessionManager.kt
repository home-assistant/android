package io.homeassistant.companion.android.frontend.session

import dagger.hilt.android.scopes.ViewModelScoped
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.authentication.SessionState
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.frontend.error.FrontendConnectionError
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

/**
 * Manages server session authentication operations.
 *
 * This class provides methods to check session state, retrieve external authentication
 * for JavaScript callbacks, and revoke sessions. It returns sealed result types that
 * allow the ViewModel to handle different outcomes appropriately.
 */
@ViewModelScoped
class ServerSessionManager @Inject constructor(private val serverManager: ServerManager) {
    /**
     * Check if server has an authenticated session.
     *
     * @param serverId The server ID to check
     * @return `true` if authenticated, `false` otherwise
     */
    suspend fun isSessionConnected(serverId: Int): Boolean {
        return try {
            serverManager.authenticationRepository(serverId).getSessionState() == SessionState.CONNECTED
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Unable to get server session state")
            false
        }
    }

    /**
     * Retrieve external authentication for JavaScript callback.
     *
     * @param serverId The server ID to authenticate with
     * @param payload The auth payload containing callback name and force refresh flag
     * @return [ExternalAuthResult.Success] with callback script on success,
     *         or [ExternalAuthResult.Failed] with error for anonymous sessions
     */
    suspend fun getExternalAuth(serverId: Int, payload: AuthPayload): ExternalAuthResult {
        return try {
            val authJson = serverManager.authenticationRepository(serverId)
                .retrieveExternalAuthentication(payload.force)
            Timber.d("External auth retrieved successfully")
            ExternalAuthResult.Success(
                callbackScript = "${payload.callback}(true, $authJson)",
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Unable to retrieve external auth")

            val isAnonymousSession = try {
                serverManager.authenticationRepository(serverId)
                    .getSessionState() == SessionState.ANONYMOUS
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                true
            }

            val error = if (isAnonymousSession) {
                FrontendConnectionError.AuthenticationError(
                    message = commonR.string.error_connection_failed,
                    errorDetails = e.message ?: "Authentication failed",
                    rawErrorType = "ExternalAuthFailed",
                )
            } else {
                null
            }

            ExternalAuthResult.Failed(
                callbackScript = "${payload.callback}(false)",
                error = error,
            )
        }
    }

    /**
     * Revoke session for JavaScript callback.
     *
     * @param serverId The server ID to revoke session for
     * @param payload The auth payload containing callback name
     * @return [RevokeAuthResult.Success] or [RevokeAuthResult.Failed] with callback script
     */
    suspend fun revokeExternalAuth(serverId: Int, payload: AuthPayload): RevokeAuthResult {
        return try {
            serverManager.authenticationRepository(serverId).revokeSession()
            Timber.d("External auth revoked successfully")
            RevokeAuthResult.Success(callbackScript = "${payload.callback}(true)")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Unable to revoke external auth")
            RevokeAuthResult.Failed(callbackScript = "${payload.callback}(false)")
        }
    }
}
