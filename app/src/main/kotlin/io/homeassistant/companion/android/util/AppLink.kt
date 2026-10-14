package io.homeassistant.companion.android.util

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import io.homeassistant.companion.android.common.util.appOrStoreIntent
import io.homeassistant.companion.android.common.util.intentUriOrStoreIntent
import io.homeassistant.companion.android.frontend.navigation.FrontendTarget
import io.homeassistant.companion.android.launch.intentLaunchWithNavigateTo
import io.homeassistant.companion.android.settings.SettingsActivity

private const val APP_PREFIX = "app://"
private const val INTENT_PREFIX = "intent:"
private const val SETTINGS_PREFIX = "settings://"
private const val NOTIFICATION_HISTORY = "notification_history"
private const val DEEP_LINK_PREFIX = "deep-link://"

/**
 * A link the user or the server configured to be opened from outside the frontend, such as the tap action of a
 * notification or a widget.
 *
 * Use [parse] to read a raw link and [createAppLinkIntent] to open it, so every entry point supports the same
 * syntax.
 */
internal sealed interface AppLink {

    /** Launch the installed app with [packageName] (`app://<package>`). */
    data class App(val packageName: String) : AppLink

    /** Launch an Android `intent:` URI. */
    data class IntentUri(val uri: String) : AppLink

    /** Open the notification history screen (`settings://notification_history`). */
    data object NotificationHistory : AppLink

    /** Open [uri] in another app, from an absolute URL or a `deep-link://` link. */
    data class External(val uri: String) : AppLink

    /** Navigate to [target] in the Home Assistant frontend (relative path, `entityId:` or blank). */
    data class Frontend(val target: FrontendTarget) : AppLink

    companion object {
        /**
         * Parses a raw link, ignoring surrounding whitespace.
         *
         * Unknown `settings://` pages and blank links open the default dashboard.
         */
        fun parse(rawLink: String): AppLink {
            val link = rawLink.trim()
            return when {
                link.startsWith(APP_PREFIX) -> App(link.removePrefix(APP_PREFIX))
                link.startsWith(INTENT_PREFIX) -> IntentUri(link)
                link.startsWith(SETTINGS_PREFIX) -> if (link.removePrefix(SETTINGS_PREFIX) == NOTIFICATION_HISTORY) {
                    NotificationHistory
                } else {
                    Frontend(FrontendTarget.Default)
                }
                link.startsWith(DEEP_LINK_PREFIX) -> External(link.removePrefix(DEEP_LINK_PREFIX))
                UrlUtil.isAbsoluteUrl(link) -> External(link)
                else -> Frontend(FrontendTarget.fromRawPath(link))
            }
        }
    }
}

/**
 * Creates an [Intent] opening [link] (see [AppLink.parse] for the supported syntax) that can be started from a
 * non-activity context, such as a notification or a widget.
 *
 * Apps that are not installed open their Play Store listing, and `intent:` URIs that cannot be parsed open the
 * default dashboard of the server [serverId].
 */
internal fun Context.createAppLinkIntent(link: String, serverId: Int): Intent =
    createAppLinkIntent(AppLink.parse(link), serverId)

/** Creates an [Intent] opening [appLink], see [createAppLinkIntent]. */
internal fun Context.createAppLinkIntent(appLink: AppLink, serverId: Int): Intent {
    val intent = when (appLink) {
        is AppLink.App -> appOrStoreIntent(appLink.packageName)
        is AppLink.IntentUri -> intentUriOrStoreIntent(appLink.uri)
            ?: intentLaunchWithNavigateTo(FrontendTarget.Default, serverId).asInAppIntent()
        is AppLink.External -> Intent(Intent.ACTION_VIEW, appLink.uri.toUri())
        AppLink.NotificationHistory ->
            SettingsActivity.newInstance(this, SettingsActivity.Deeplink.NotificationHistory).asInAppIntent()
        is AppLink.Frontend -> intentLaunchWithNavigateTo(appLink.target, serverId).asInAppIntent()
    }
    return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/** Opens screens of this app in their own task, kept out of recents, like the frontend opened from a notification. */
private fun Intent.asInAppIntent(): Intent =
    addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
