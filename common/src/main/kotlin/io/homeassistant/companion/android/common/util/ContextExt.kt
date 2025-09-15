package io.homeassistant.companion.android.common.util

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
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
    if (!isIgnoringBatteryOptimizations()) {
        startActivity(
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                "package:$packageName".toUri(),
            ),
        )
    }
}

/**
 * Checks if the app is ignoring battery optimizations.
 *
 * TODO this should not be exposed to the wear module https://github.com/home-assistant/android/discussions/5771
 * @return `true` if the app is ignoring battery optimizations, `false` otherwise.
 */
fun Context.isIgnoringBatteryOptimizations(): Boolean {
    return Build.VERSION.SDK_INT <= Build.VERSION_CODES.M ||
        getSystemService<PowerManager>()
            ?.isIgnoringBatteryOptimizations(packageName ?: "")
            ?: false
}
