package io.homeassistant.companion.android.common.util

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.util.AndroidRuntimeException
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import io.homeassistant.companion.android.common.BuildConfig
import io.homeassistant.companion.android.common.R
import java.net.URISyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

private const val MARKET_PREFIX = "https://play.google.com/store/apps/details?id="

/**
 * Wrapper around [Context.getSharedPreferences] that uses [Dispatchers.IO] to ensure
 * that the read from the disk never blocks the main thread.
 *
 * @param name Desired preferences file.
 * @param mode Operating mode.
 *
 * @return The SharedPreferences instance.
 *
 * @see Context.getSharedPreferences
 */
suspend fun Context.getSharedPreferencesSuspend(name: String, mode: Int = Context.MODE_PRIVATE): SharedPreferences {
    return withContext(Dispatchers.IO) {
        getSharedPreferences(
            name,
            mode,
        )
    }
}

/**
 * Checks if the current device is an Android Automotive OS device.
 *
 * @return `true` if the device is an Android Automotive OS device, `false` otherwise.
 */
fun Context.isAutomotive(): Boolean {
    return packageManager.isAutomotive()
}

/**
 * If the app is not already ignoring battery optimizations, this function will open the system
 * settings page to allow the user to grant this permission.
 *
 * TODO this should not be exposed to the wear module https://github.com/home-assistant/android/discussions/5771
 */
fun Context.maybeAskForIgnoringBatteryOptimizations() {
    createBatteryOptimizationIntent()?.let { startActivity(it) }
}

/**
 * Creates an [Intent] to request ignoring battery optimizations.
 *
 * This intent can be used with an [androidx.activity.result.ActivityResultLauncher] to
 * wait for the user to respond to the battery optimization dialog before proceeding.
 *
 * @return An [Intent] configured to request battery optimization exemption, or `null` if
 *         the app is already ignoring battery optimizations or the intent cannot be resolved
 *         (some OEM devices don't support this intent).
 */
// Suppressing QueryPermissionsNeeded: System Settings intents are always visible per Android's
// package visibility documentation, and the app has QUERY_ALL_PACKAGES permission.
@SuppressLint("BatteryLife", "QueryPermissionsNeeded")
fun Context.createBatteryOptimizationIntent(): Intent? {
    if (isIgnoringBatteryOptimizations()) return null

    val intent = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        "package:$packageName".toUri(),
    )
    return if (intent.resolveActivity(packageManager) != null) {
        intent
    } else {
        null
    }
}

/**
 * Checks if the app is ignoring battery optimizations.
 *
 * @return `true` if the app is ignoring battery optimizations, `false` otherwise.
 */
fun Context.isIgnoringBatteryOptimizations(): Boolean {
    return getSystemService<PowerManager>()
        ?.isIgnoringBatteryOptimizations(packageName ?: "")
        ?: false
}

suspend fun Context.openUri(uri: String, onShowSnackbar: suspend (message: String, action: String?) -> Boolean) {
    startActivityOrShowSnackbar(
        intent = Intent(Intent.ACTION_VIEW, uri.toUri()),
        target = uri,
        onShowSnackbar = onShowSnackbar,
    )
}

/**
 * Builds an [Intent] that opens this app's "App info" page in the system Settings.
 */
fun Context.createSystemAppSettingsIntent(): Intent {
    return Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    )
}

/**
 * Opens this app's "App info" page in the system Settings.
 */
fun Context.openSystemAppSettings() {
    startActivity(
        createSystemAppSettingsIntent(),
    )
}

/**
 * Parses an `intent:` URI coming from an untrusted source (for example a link in the web
 * frontend or a server-sent notification) into a launchable [Intent], neutralizing intent
 * redirection in the process (see [Intent.stripSelfNonExportedTarget]).
 *
 * Use this instead of [Intent.parseUri] for any URI we do not fully control, so the
 * sanitization can never be forgotten at a call site.
 *
 * @return the parsed and sanitized [Intent]
 * @throws java.net.URISyntaxException if the basic URI syntax
 * is bad (as parsed by the Uri class) or the Intent data within the
 * URI is invalid.
 */
fun Context.parseExternalIntentUri(uri: String): Intent =
    Intent.parseUri(uri, Intent.URI_INTENT_SCHEME).stripSelfNonExportedTarget(this)

/**
 * Launches the installed app identified by [packageName].
 *
 * When no app with the given package is installed, the Play Store listing for that package is opened instead.
 * When nothing can handle the launch (e.g. no store on the device), [onShowSnackbar] surfaces the failure to
 * the user instead of failing silently.
 */
suspend fun Context.launchAppOrStore(
    packageName: String,
    onShowSnackbar: suspend (message: String, action: String?) -> Boolean,
) {
    startActivityOrShowSnackbar(
        intent = appOrStoreIntent(packageName),
        target = packageName,
        onShowSnackbar = onShowSnackbar,
    )
}

/**
 * Parses [intentUri] as an Android `intent:` URI and launches it.
 *
 * When the parsed intent targets a package that is not installed, the Play Store listing for that package is
 * opened instead. Parse failures and cases where nothing can handle the launch surface [onShowSnackbar] to the
 * user instead of failing silently.
 */
suspend fun Context.launchIntentUri(
    intentUri: String,
    onShowSnackbar: suspend (message: String, action: String?) -> Boolean,
) {
    val intent = try {
        parseExternalIntentUri(intentUri)
    } catch (e: URISyntaxException) {
        // Don't log e in release to not leak the URI in the log
        Timber.e(e.takeIf { BuildConfig.DEBUG }, "Unable to parse intent URI")
        onShowSnackbar(getString(R.string.fail_to_navigate_to_uri, intentUri), null)
        return
    }

    val targetPackage = intent.`package`
    val isPackageInstalled = targetPackage?.let { packageManager.getLaunchIntentForPackage(it) } != null
    val toLaunch = if (!isPackageInstalled && !targetPackage.isNullOrEmpty()) {
        Timber.w("No app found for intent, opening app store")
        Intent(Intent.ACTION_VIEW, (MARKET_PREFIX + targetPackage).toUri())
    } else {
        intent
    }
    startActivityOrShowSnackbar(
        intent = toLaunch,
        target = intentUri,
        onShowSnackbar = onShowSnackbar,
    )
}

/**
 * Opens the OS security settings screen, where the user can install a client certificate.
 *
 * When no activity can handle the intent, [onShowSnackbar] surfaces the failure to the user.
 */
suspend fun Context.openSecuritySettings(onShowSnackbar: suspend (message: String, action: String?) -> Boolean) {
    startActivityOrShowSnackbar(
        intent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK },
        target = getString(R.string.security_settings),
        onShowSnackbar = onShowSnackbar,
    )
}

/**
 * Builds an intent that opens [packageName]'s launcher activity when installed, or its Play Store listing
 * otherwise.
 */
private fun Context.appOrStoreIntent(packageName: String): Intent {
    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
    return if (launchIntent != null) {
        launchIntent.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
    } else {
        Timber.w("No launch intent for package, opening app store")
        Intent(Intent.ACTION_VIEW, (MARKET_PREFIX + packageName).toUri())
    }
}

/**
 * Starts [intent], showing "no app available to open [target]" via [onShowSnackbar] when no activity can
 * handle it. [target] is the user-facing description of what we tried to open (a URI, package, or label).
 */
private suspend fun Context.startActivityOrShowSnackbar(
    intent: Intent,
    target: String,
    onShowSnackbar: suspend (message: String, action: String?) -> Boolean,
) {
    if (!startActivityCatching(intent)) {
        onShowSnackbar(getString(R.string.fail_to_navigate_to_uri, target), null)
    }
}

/**
 * Starts [intent], returning `true` on success and `false` (after logging) when the launch fails.
 *
 * The caught exceptions embed the intent (including its data URI), so they are only logged in debug builds
 * to avoid leaking user data in release logs.
 */
private fun Context.startActivityCatching(intent: Intent): Boolean {
    return try {
        startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        Timber.e(e.takeIf { BuildConfig.DEBUG }, "No activity found to handle intent")
        false
    } catch (e: SecurityException) {
        // The resolved activity requires a permission we were not granted to launch it.
        Timber.e(e.takeIf { BuildConfig.DEBUG }, "Not allowed to launch intent")
        false
    } catch (e: AndroidRuntimeException) {
        // e.g. launching without FLAG_ACTIVITY_NEW_TASK from a non-Activity context.
        Timber.e(e.takeIf { BuildConfig.DEBUG }, "Unable to launch intent")
        false
    }
}
