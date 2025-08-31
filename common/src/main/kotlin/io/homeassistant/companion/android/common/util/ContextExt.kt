package io.homeassistant.companion.android.common.util

import android.content.Context
import android.content.SharedPreferences
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
