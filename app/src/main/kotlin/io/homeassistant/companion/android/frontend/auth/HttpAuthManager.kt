package io.homeassistant.companion.android.frontend.auth

import android.content.Context
import android.webkit.HttpAuthHandler
import dagger.hilt.android.scopes.ViewModelScoped
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.database.authentication.Authentication
import io.homeassistant.companion.android.database.authentication.AuthenticationDao
import io.homeassistant.companion.android.frontend.dialog.FrontendDialogManager
import io.homeassistant.companion.android.frontend.dialog.HttpAuthOutcome
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Time window within which a repeated auth request indicates rejected credentials. */
private val RAPID_REAUTH_THRESHOLD = 500.milliseconds

/**
 * The outcome of handling an HTTP Basic Auth request.
 */
internal sealed interface HttpAuthResult {
    /** Stored credentials were found and used to auto-proceed. No dialog was shown. */
    data object AutoProceeded : HttpAuthResult

    /** A dialog was shown and the user supplied credentials; the [HttpAuthHandler] has been told to proceed. */
    data object Proceeded : HttpAuthResult

    /** A dialog was shown and the user cancelled; the [HttpAuthHandler] has been told to cancel. */
    data object Cancelled : HttpAuthResult
}

/**
 * Handles HTTP Basic Auth requests from the WebView end-to-end.
 *
 * Looks up stored credentials from [AuthenticationDao] and auto-proceeds when possible. Otherwise
 * shows a credential dialog through [FrontendDialogManager], suspends until the user responds,
 * resolves the [HttpAuthHandler], persists credentials if requested, and returns an
 * [HttpAuthResult] describing what happened so the caller can react (e.g. emit a snackbar on cancel).
 *
 * Detects rapid re-authentication (within [RAPID_REAUTH_THRESHOLD]) as an indication that stored
 * credentials were rejected by the server, in which case the dialog is surfaced with the auth-error
 * variant and any persisted credentials are updated rather than inserted.
 *
 * Uses [Clock] for timing to support deterministic testing.
 */
@OptIn(ExperimentalTime::class)
@ViewModelScoped
internal class HttpAuthManager @Inject constructor(
    private val authenticationDao: AuthenticationDao,
    private val clock: Clock,
    private val dialogManager: FrontendDialogManager,
) {
    private var lastAutoProceededAt: Instant = Instant.DISTANT_PAST

    /**
     * Resolves an HTTP Basic Auth request — auto-proceeds with stored credentials, or shows a dialog
     * and waits for the user to respond.
     *
     * The [handler] is always resolved (`proceed` or `cancel`) before this function returns.
     *
     * @param handler The [HttpAuthHandler] to resolve with the user's choice
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

        return when (
            val outcome = dialogManager.showHttpAuth(
                host = host,
                message = formatAuthMessage(host = host, resource = resource),
            )
        ) {
            is HttpAuthOutcome.Proceed -> {
                handler.proceed(outcome.username, outcome.password)
                if (outcome.remember) {
                    persistCredentials(
                        hostKey = hostKey,
                        username = outcome.username,
                        password = outcome.password,
                        isUpdate = isRapidReauth,
                    )
                }
                HttpAuthResult.Proceeded
            }
            HttpAuthOutcome.Cancel -> {
                handler.cancel()
                HttpAuthResult.Cancelled
            }
        }
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
