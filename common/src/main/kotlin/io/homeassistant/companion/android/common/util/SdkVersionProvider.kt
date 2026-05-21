package io.homeassistant.companion.android.common.util

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.VisibleForTesting

/**
 * Reports whether the current Android SDK level is at least [api]. Tests can substitute a fake
 * provider to simulate a SDK version.
 *
 * The [ChecksSdkIntAtLeast] annotation tells Android Lint that calls to [isAtLeast] are valid
 * SDK version gates, so callers can reference newer-API symbols below the check without needing
 * `@SuppressLint("NewApi")`.
 */
fun interface SdkVersionProvider {
    @ChecksSdkIntAtLeast(parameter = 0)
    fun isAtLeast(api: Int): Boolean
}

/**
 * Default [SdkVersionProvider] backed by the real device's [Build.VERSION.SDK_INT]. This is the
 * single intentional raw read of `SDK_INT` in production code, so the `SdkVersionAccess` Lint
 * rule is explicitly suppressed.
 *
 * The setter is [VisibleForTesting] only: tests that need to simulate a different SDK level
 * can assign a fake here and restore the original in `@AfterEach`.
 */
@SuppressLint("SdkVersionAccess")
var sdkVersion: SdkVersionProvider = object : SdkVersionProvider {
    @ChecksSdkIntAtLeast(parameter = 0)
    override fun isAtLeast(api: Int): Boolean {
        return Build.VERSION.SDK_INT >= api
    }

    /** Returns the device's `SDK_INT` as a string, for use in diagnostic logging and registration payloads. */
    override fun toString(): String {
        return Build.VERSION.SDK_INT.toString()
    }
}
    @VisibleForTesting set
