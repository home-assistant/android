package io.homeassistant.companion.android.common.util

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.VisibleForTesting

/**
 * Reports whether the current Android SDK level is at least a given version, backed by the real
 * device's [Build.VERSION.SDK_INT]. This is the single intentional raw read of `SDK_INT` in
 * production code, so the `SdkVersionAccess` Lint rule is explicitly suppressed.
 *
 * The [ChecksSdkIntAtLeast] annotation tells Android Lint that calls to [isAtLeast] are valid
 * SDK version gates, so callers can reference newer-API symbols below the check without needing
 * `@SuppressLint("NewApi")`.
 *
 * The [sdkInt] setter is [VisibleForTesting] only: tests that need to simulate a different SDK
 * level can assign it.
 */
@SuppressLint("SdkVersionAccess")
object SdkVersion {
    var sdkInt = Build.VERSION.SDK_INT
        @VisibleForTesting set

    @ChecksSdkIntAtLeast(parameter = 0)
    fun isAtLeast(version: Int): Boolean {
        return sdkInt >= version
    }

    /** Returns the device's [sdkInt] as a string, for use in diagnostic logging and registration payloads. */
    override fun toString(): String {
        return sdkInt.toString()
    }
}
