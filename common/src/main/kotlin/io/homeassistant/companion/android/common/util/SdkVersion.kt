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
 * level can assign it, and should call [resetSdkInt] afterwards to avoid leaking the override.
 */
@SuppressLint("SdkVersionAccess")
object SdkVersion {
    var sdkInt = Build.VERSION.SDK_INT
        @VisibleForTesting set

    @ChecksSdkIntAtLeast(parameter = 0)
    fun isAtLeast(version: Int): Boolean {
        // On a real device SDK_INT is always non-zero, so this never fires in production. It is 0
        // only when Build.VERSION.SDK_INT is unavailable, i.e. a plain JVM unit test that reached
        // this code without configuring the SDK level. FailFast surfaces that as a test failure so
        // the test sets a level explicitly (or uses Robolectric with @Config(sdk = ...)) instead of
        // silently treating every gate as "below version".
        FailFast.failWhen(sdkInt == 0) {
            "SdkVersion.sdkInt is 0: set SdkVersion.sdkInt in the test (and reset it afterwards), " +
                "or use Robolectric with @Config(sdk = ...)"
        }
        return sdkInt >= version
    }

    /**
     * Restores [sdkInt] to the real device value ([Build.VERSION.SDK_INT]).
     *
     * Intended for test teardown so that a value assigned via the [VisibleForTesting] setter does
     * not leak into subsequent tests. In a plain JVM test this resets to 0, which makes a later
     * [isAtLeast] call fail fast unless the test sets the level again.
     */
    @VisibleForTesting
    fun resetSdkInt() {
        sdkInt = Build.VERSION.SDK_INT
    }

    /** Returns the device's [sdkInt] as a string, for use in diagnostic logging and registration payloads. */
    override fun toString(): String {
        return sdkInt.toString()
    }
}
