package io.homeassistant.companion.android.frontend.auth

import android.content.Context
import android.webkit.HttpAuthHandler
import dagger.hilt.android.scopes.ViewModelScoped
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.database.authentication.Authentication
import io.homeassistant.companion.android.database.authentication.AuthenticationDao
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Time window within which a repeated auth request indicates rejected credentials. */
private val RAPID_REAUTH_THRESHOLD = 500.milliseconds

/**
 * Result of handling an HTTP Basic Auth request.
 */
sealed interface HttpAuthResult {
    /** Stored credentials were found and used to auto-proceed. No dialog needed. */
    data object AutoProceeded : HttpAuthResult

    /**
     * No stored credentials or stored credentials were rejected. A dialog should be shown.
     *
     * @param host The host requesting authentication
     * @param message Formatted message
     * @param isAuthError Whether this is a re-auth after stored credentials were rejected
     * @param hostKey The credential storage key
     */
    data class ShowDialog(
        val host: String,
        val message: (Context) -> String,
        val isAuthError: Boolean,
        val hostKey: String,
        val onProceed: suspend (username: String, password: String, remember: Boolean) -> Unit,
        val onCancel: () -> Unit,
    ) : HttpAuthResult
}

/**
 * Handles HTTP Basic Auth requests from the WebView.
 *
 * Looks up stored credentials from [AuthenticationDao] and auto-proceeds when possible.
 * Detects rapid re-authentication (within [RAPID_REAUTH_THRESHOLD]) as an indication that stored credentials
 * were rejected by the server. Uses [Clock] for timing to support deterministic testing.
 */
@OptIn(ExperimentalTime::class)
@ViewModelScoped
class HttpAuthManager @Inject constructor(private val authenticationDao: AuthenticationDao, private val clock: Clock) {
    private var lastAutoProceededAt: Instant = Instant.DISTANT_PAST

    /**
     * Checks stored credentials and either auto-proceeds or requests a dialog.
     *
     * @param handler The [HttpAuthHandler] to proceed with if stored credentials exist
     * @param host The host requesting authentication
     * @param resource The URL being loaded (used for credential key and scheme detection)
     * @param realm The authentication realm
     */
    suspend fun handleAuthRequest(
        handler: HttpAuthHandler,
        host: String,
        resource: String,
        realm: String,
    ): HttpAuthResult {
        val hostKey = resource + realm
        val storedAuth = authenticationDao.get(hostKey)
        val isRapidReauth = (clock.now() - lastAutoProceededAt) < RAPID_REAUTH_THRESHOLD

        if (storedAuth != null && !isRapidReauth) {
            handler.proceed(storedAuth.username, storedAuth.password)
            lastAutoProceededAt = clock.now()
            return HttpAuthResult.AutoProceeded
        }

        return HttpAuthResult.ShowDialog(
            host = host,
            message = formatAuthMessage(host = host, resource = resource),
            isAuthError = isRapidReauth,
            hostKey = hostKey,
            onProceed = { username, password, remember ->
                handler.proceed(username, password)
                if (remember) {
                    persistCredentials(
                        hostKey = hostKey,
                        username = username,
                        password = password,
                        isUpdate = isRapidReauth,
                    )
                }
            },
            onCancel = { handler.cancel() },
        )
    }

    private suspend fun persistCredentials(hostKey: String, username: String, password: String, isUpdate: Boolean) {
        val auth = Authentication(host = hostKey, username = username, password = password)
        if (isUpdate) {
            authenticationDao.update(auth)
        } else {
            authenticationDao.insert(auth)
        }
    }

    private fun formatAuthMessage(host: String, resource: String): (Context) -> String {
        val isHttp = resource.startsWith("http:")
        return { context ->
            val requiredFields = context.getString(commonR.string.required_fields)
            if (isHttp) {
                val notPrivate = context.getString(commonR.string.not_private)
                "http://$host $requiredFields $notPrivate"
            } else {
                "https://$host $requiredFields"
            }
        }
    }
}
