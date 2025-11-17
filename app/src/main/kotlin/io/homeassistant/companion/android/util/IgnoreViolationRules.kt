package io.homeassistant.companion.android.util

import android.os.Build
import android.os.strictmode.DiskReadViolation
import android.os.strictmode.DiskWriteViolation
import android.os.strictmode.IncorrectContextUseViolation
import android.os.strictmode.Violation
import androidx.annotation.RequiresApi
import io.homeassistant.companion.android.common.util.IgnoreViolationRule

val vmPolicyIgnoredViolationRules = listOf(
    IgnoreChromiumTrichomeWrongContextUsage,
    IgnoreBarcodeScannerRotationListenerWrongContextUsage,
)

val threadPolicyIgnoredViolationRules = listOf(
    IgnoreChangelogDiskRead,
    IgnoreNotificationHistoryFragmentLoadSharedPrefDiskRead,
    IgnoreComposeTextContextMenuDiskRead,
    IgnoreActivityThreadVsyncDiskReadWrite,
    IgnoreSamsungInputRuneDiskRead,
    IgnoreSamsungKnoxProKioskDiskRead,
    IgnoreAndroidAutoServiceConnectionDiskRead,
    IgnoreAndroidAutoRendererServiceDiskRead,
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
private data object IgnoreChromiumTrichomeWrongContextUsage : IgnoreViolationRule {
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
private data object IgnoreBarcodeScannerRotationListenerWrongContextUsage : IgnoreViolationRule {
    @RequiresApi(Build.VERSION_CODES.S)
    override fun shouldIgnore(violation: Violation): Boolean {
        if (violation !is IncorrectContextUseViolation) return false

        return violation.stackTrace.any {
            it.className == "com.journeyapps.barcodescanner.RotationListener" &&
                it.methodName == "listen"
        }
    }
}

/**
 * Ignore a DiskReadViolation inside https://github.com/AppDevNext/ChangeLog while
 * loading default shared preferences.
 */
private data object IgnoreChangelogDiskRead : IgnoreViolationRule {
    @RequiresApi(Build.VERSION_CODES.P)
    override fun shouldIgnore(violation: Violation): Boolean {
        if (violation !is DiskReadViolation) return false
        return violation.stackTrace.any {
            it.className == "info.hannes.changelog.ChangeLog"
        }
    }
}

/**
 * Ignore a DiskReadViolation inside [NotificationHistoryFragment] while loading the XML that contains the
 * preferences used to make the UI of the screen. See the class for more details.
 */
private data object IgnoreNotificationHistoryFragmentLoadSharedPrefDiskRead : IgnoreViolationRule {
    @RequiresApi(Build.VERSION_CODES.P)
    override fun shouldIgnore(violation: Violation): Boolean {
        if (violation !is DiskReadViolation) return false
        return violation.stackTrace.any {
            it.className == "io.homeassistant.companion.android.settings.notification.NotificationHistoryFragment" &&
                it.methodName == "onCreatePreferences"
        }
    }
}

/**
 * Ignore a DiskReadViolation in Jetpack Compose's text selection context menu implementation.
 * This occurs when using SelectionContainer which enables text selection and shows a context menu.
 */
private data object IgnoreComposeTextContextMenuDiskRead : IgnoreViolationRule {
    @RequiresApi(Build.VERSION_CODES.P)
    override fun shouldIgnore(violation: Violation): Boolean {
        if (violation !is DiskReadViolation) return false
        return violation.stackTrace.any {
            it.className ==
                "androidx.compose.foundation.text.contextmenu.internal.AndroidTextContextMenuToolbarProvider"
        }
    }
}

/**
 * Ignore an [DiskWriteViolation] and [DiskReadViolation] in Android's ActivityThread vsync scheduling.
 * This occurs in the framework's internal vsync scheduling mechanism and is beyond
 * application control.
 */
private data object IgnoreActivityThreadVsyncDiskReadWrite : IgnoreViolationRule {
    @RequiresApi(Build.VERSION_CODES.P)
    override fun shouldIgnore(violation: Violation): Boolean {
        if (violation !is DiskWriteViolation && violation !is DiskReadViolation) return false

        return violation.stackTrace.any {
            it.className == "android.app.ActivityThread" &&
                it.methodName == "scheduleVsyncSS"
        }
    }
}

/**
 * Ignore a [DiskReadViolation] in Samsung's InputRune framework component.
 * This occurs in Samsung's internal input configuration system and is beyond
 * application control.
 */
private data object IgnoreSamsungInputRuneDiskRead : IgnoreViolationRule {
    @RequiresApi(Build.VERSION_CODES.P)
    override fun shouldIgnore(violation: Violation): Boolean {
        if (violation !is DiskReadViolation) return false

        return violation.stackTrace.any {
            it.className == "com.samsung.android.rune.InputRune"
        }
    }
}

/**
 * Ignore a [DiskReadViolation] in Samsung Knox's ProKioskManager.
 * This occurs when Samsung Knox checks the kiosk state and is beyond application control.
 */
private data object IgnoreSamsungKnoxProKioskDiskRead : IgnoreViolationRule {
    @RequiresApi(Build.VERSION_CODES.P)
    override fun shouldIgnore(violation: Violation): Boolean {
        if (violation !is DiskReadViolation) return false

        return violation.stackTrace.any {
            it.className == "com.samsung.android.knox.custom.ProKioskManager" &&
                it.methodName == "getProKioskState"
        }
    }
}

/**
 * Ignore a [DiskReadViolation] in Android Auto/Automotive's ServiceConnectionManager.
 * This occurs when the Android Auto library initializes its service connection and is
 * beyond application control.
 */
private data object IgnoreAndroidAutoServiceConnectionDiskRead : IgnoreViolationRule {
    @RequiresApi(Build.VERSION_CODES.P)
    override fun shouldIgnore(violation: Violation): Boolean {
        if (violation !is DiskReadViolation) return false

        return violation.stackTrace.any {
            it.className == "androidx.car.app.activity.ServiceConnectionManager" &&
                it.methodName == "initializeService"
        }
    }
}

/**
 * Ignore a [DiskReadViolation] in Android Auto/Automotive's IRendererService.
 * This occurs when the Android Auto renderer service handles binder transactions and is
 * beyond application control.
 */
private data object IgnoreAndroidAutoRendererServiceDiskRead : IgnoreViolationRule {
    @RequiresApi(Build.VERSION_CODES.P)
    override fun shouldIgnore(violation: Violation): Boolean {
        if (violation !is DiskReadViolation) return false

        return violation.stackTrace.any {
            it.className == "androidx.car.app.activity.renderer.IRendererService\$Stub" &&
                it.methodName == "onTransact"
        }
    }
}
