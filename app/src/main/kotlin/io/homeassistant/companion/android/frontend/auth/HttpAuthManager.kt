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
 * resolves the [HttpAuthHandler], persists credentials if requested (updating an existing entry
 * when one is present, inserting otherwise), and returns an [HttpAuthResult] describing what
 * happened so the caller can react.
 *
 * Two complementary signals derived from the previous proceed govern the credential-rejection flow:
 * - "Same hostKey as last proceed" surfaces the dialog's auth-error variant. The WebView caches
 *   Basic Auth in-session, so any re-challenge for a hostKey we already answered is almost
 *   certainly the server rejecting those credentials.
 * - "Rapid re-authentication" (same hostKey within [RAPID_REAUTH_THRESHOLD]) gates auto-proceed.
 *   Only this stronger signal suppresses reusing stored credentials, so legitimate re-challenges
 *   outside the window (cache eviction, server cycling the realm) still benefit from auto-proceed.
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
    /**
     * Timestamp of the last [HttpAuthHandler.proceed] for [lastProceededHostKey], or
     * [Instant.DISTANT_PAST] if none. Combined with the host key, this lets us detect a rapid
     * re-authentication attempt: if the server bounces a `proceed` back fast enough on the *same*
     * resource+realm, the follow-up request lands here within [RAPID_REAUTH_THRESHOLD] —
     * regardless of whether the credentials came from auto-proceed or from a manual dialog (with
     * or without `remember`). Tracking the key avoids tainting an unrelated resource that happens
     * to challenge for credentials right after a successful proceed on a different one.
     */
    private var lastProceededAt: Instant = Instant.DISTANT_PAST
    private var lastProceededHostKey: String? = null

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
        val isSameHostAsLastProceed = lastProceededHostKey == hostKey
        val isRapidReauth = isSameHostAsLastProceed &&
            (clock.now() - lastProceededAt) < RAPID_REAUTH_THRESHOLD

        if (storedAuth != null && !isRapidReauth) {
            handler.proceed(hostKey = hostKey, username = storedAuth.username, password = storedAuth.password)
            return HttpAuthResult.AutoProceeded
        }

        return when (
            val outcome = dialogManager.showHttpAuth(
                host = host,
                message = formatAuthMessage(host = host, resource = resource),
                isAuthError = isSameHostAsLastProceed,
            )
        ) {
            is HttpAuthOutcome.Proceed -> {
                handler.proceed(hostKey = hostKey, username = outcome.username, password = outcome.password)
                if (outcome.remember) {
                    persistCredentials(
                        hostKey = hostKey,
                        username = outcome.username,
                        password = outcome.password,
                        isUpdate = storedAuth != null,
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

    private fun HttpAuthHandler.proceed(hostKey: String, username: String, password: String) {
        proceed(username, password)
        lastProceededAt = clock.now()
        lastProceededHostKey = hostKey
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
