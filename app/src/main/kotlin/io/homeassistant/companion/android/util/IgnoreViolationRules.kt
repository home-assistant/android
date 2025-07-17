package io.homeassistant.companion.android.util

import android.os.Build
import android.os.strictmode.IncorrectContextUseViolation
import android.os.strictmode.Violation
import androidx.annotation.RequiresApi
import io.homeassistant.companion.android.common.util.IgnoreViolationRule

val ignoredViolationRules = listOf(
    IgnoreChromiumTrichomeWrongContextUsage,
    IgnoreBarcodeScannerRotationListenerWrongContextUsage,
)

/**
 * Ignore an [IncorrectContextUseViolation] that can occur
 * in the Chromium WebView client (specifically involving `chromium-TrichromeWebViewGoogle`).
 *
 * This issue typically arises when the application context is incorrectly used during
 * configuration changes (e.g., screen rotation) within the WebView's internal mechanisms.
 *
 * It doesn't seem to be tracked anywhere.
 */
private object IgnoreChromiumTrichomeWrongContextUsage : IgnoreViolationRule {
    @RequiresApi(Build.VERSION_CODES.S)
    override fun shouldIgnore(violation: Violation): Boolean {
        if (violation !is IncorrectContextUseViolation) return false

        return violation.stackTrace.any {
            it.fileName?.startsWith("chromium-TrichromeWebViewGoogle") == true &&
                it.methodName == "onConfigurationChanged"
        }
    }
}

/**
 * Ignores an IncorrectContextUseViolation specifically caused by the
 * com.journeyapps.barcodescanner.RotationListener using the application context
 * to get the WindowManager, which is incorrect for UI operations.
 *
 * This is a known issue in the zxing-android-embedded library.
 * See:
 * - https://github.com/journeyapps/zxing-android-embedded/issues/762
 * - https://github.com/journeyapps/zxing-android-embedded/blob/d09b7c76c3124fbfbd096a65d60b1997f37ff90f/zxing-android-embedded/src/com/journeyapps/barcodescanner/RotationListener.java#L31
 */
private object IgnoreBarcodeScannerRotationListenerWrongContextUsage : IgnoreViolationRule {
    @RequiresApi(Build.VERSION_CODES.S)
    override fun shouldIgnore(violation: Violation): Boolean {
        if (violation !is IncorrectContextUseViolation) return false

        return violation.stackTrace.any {
            it.className == "com.journeyapps.barcodescanner.RotationListener" &&
                it.methodName == "listen"
        }
    }
}
