package io.homeassistant.companion.android.common.util

import android.os.Build
import android.os.strictmode.DiskReadViolation
import android.os.strictmode.DiskWriteViolation
import android.os.strictmode.Violation
import androidx.annotation.RequiresApi

/**
 * Ignore an [DiskWriteViolation] and [DiskReadViolation] in Android's ActivityThread vsync scheduling.
 * This occurs in the framework's internal vsync scheduling mechanism and is beyond
 * application control.
 */
data object IgnoreActivityThreadVsyncDiskReadWrite : IgnoreViolationRule {
    @RequiresApi(Build.VERSION_CODES.P)
    override fun shouldIgnore(violation: Violation): Boolean {
        if (violation !is DiskWriteViolation && violation !is DiskReadViolation) return false

        return violation.stackTrace.any {
            it.className == "android.app.ActivityThread" &&
                it.methodName == "scheduleVsyncSS"
        }
    }
}
