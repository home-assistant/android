package io.homeassistant.companion.android.util

import android.os.Build
import android.os.strictmode.IncorrectContextUseViolation
import android.os.strictmode.Violation
import androidx.annotation.RequiresApi
import io.homeassistant.companion.android.common.util.IgnoreViolationRule

val ignoredViolationRules = listOf(
    IgnoreChromiumTrichomeWrongContextUsage,
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
