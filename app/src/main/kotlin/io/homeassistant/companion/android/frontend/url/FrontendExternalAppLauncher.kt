package io.homeassistant.companion.android.frontend.url

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.AndroidRuntimeException
import androidx.core.net.toUri
import io.homeassistant.companion.android.common.util.parseExternalIntentUri
import java.net.URISyntaxException
import timber.log.Timber

private const val MARKET_PREFIX = "https://play.google.com/store/apps/details?id="

/**
 * Launches the installed app identified by [packageName].
 *
 * When no app with the given package is installed, the Play Store listing for that package is opened instead.
 * Failures to start any activity (e.g. no app able to handle the store URL) are logged and swallowed so a
 * malformed link from the frontend cannot crash the app.
 */
fun Context.launchAppOrStore(packageName: String) {
    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
    val intent = if (launchIntent != null) {
        launchIntent.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
    } else {
        Timber.w("No launch intent for package, opening app store")
        Intent(Intent.ACTION_VIEW, (MARKET_PREFIX + packageName).toUri())
    }
    startActivityCatching(intent)
}

/**
 * Parses [intentUri] as an Android `intent:` URI and launches it.
 *
 * When the parsed intent targets a package that is not installed, the Play Store listing for that package is
 * opened instead. Parsing and launch failures are logged and swallowed so a malformed link from the frontend
 * cannot crash the app.
 */
fun Context.launchIntentUri(intentUri: String) {
    val intent = try {
        parseExternalIntentUri(intentUri)
    } catch (e: URISyntaxException) {
        Timber.e(e, "Unable to parse intent URI")
        return
    }

    val targetPackage = intent.`package`
    val isPackageInstalled = targetPackage?.let { packageManager.getLaunchIntentForPackage(it) } != null
    if (!isPackageInstalled && !targetPackage.isNullOrEmpty()) {
        Timber.w("No app found for intent, opening app store")
        startActivityCatching(Intent(Intent.ACTION_VIEW, (MARKET_PREFIX + targetPackage).toUri()))
    } else {
        startActivityCatching(intent)
    }
}

private fun Context.startActivityCatching(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Timber.e(e, "No activity found to handle intent")
    } catch (e: SecurityException) {
        // The resolved activity requires a permission we were not granted to launch it.
        Timber.e(e, "Not allowed to launch intent")
    } catch (e: AndroidRuntimeException) {
        // e.g. launching without FLAG_ACTIVITY_NEW_TASK from a non-Activity context.
        Timber.e(e, "Unable to launch intent")
    }
}
