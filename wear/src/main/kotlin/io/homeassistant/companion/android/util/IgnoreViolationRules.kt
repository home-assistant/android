package io.homeassistant.companion.android.util

import android.os.Build
import android.os.strictmode.DiskReadViolation
import android.os.strictmode.Violation
import androidx.annotation.RequiresApi
import io.homeassistant.companion.android.common.data.HomeAssistantApis
import io.homeassistant.companion.android.common.util.IgnoreViolationRule

val threadPolicyIgnoredViolationRules: List<IgnoreViolationRule> = listOf(
    IgnoreConfigureOkHttpClientDiskRead,
)

/**
 * Ignores a [DiskReadViolation] raised while [HomeAssistantApis] configures its OkHttpClient:
 * building the TLSHelper trust managers reads CA keystores from disk on some devices.
 *
 * Tracked by https://github.com/home-assistant/android/pull/7042
 */
private data object IgnoreConfigureOkHttpClientDiskRead : IgnoreViolationRule {
    @RequiresApi(Build.VERSION_CODES.P)
    override fun shouldIgnore(violation: Violation): Boolean {
        if (violation !is DiskReadViolation) return false

        return violation.stackTrace.any {
            it.className == HomeAssistantApis::class.java.name &&
                it.methodName == "configureOkHttpClient"
        }
    }
}
