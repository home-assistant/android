package io.homeassistant.companion.android.common.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
@SuppressLint("BatteryLife")
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
