package io.homeassistant.companion.android.common.util

import androidx.compose.runtime.Composer
import androidx.compose.runtime.tooling.ComposeStackTraceMode

/**
 * Configures the global Compose diagnostic stack trace mode so that crashes occurring during
 * composition carry a composition stack trace as a suppressed exception.
 *
 * In debug builds, [ComposeStackTraceMode.SourceInformation] is used to produce precise file
 * and line numbers. It records Compose source information at runtime, which adds some
 * performance overhead and should not be used in release builds.
 *
 * In release builds, [ComposeStackTraceMode.GroupKeys] is used. It has no runtime overhead
 * and still emits a composition stack trace on crashes.
 *
 * @param isDebug whether the current build is a debug build (typically `BuildConfig.DEBUG`).
 */
fun configureComposeDiagnosticStackTrace(isDebug: Boolean) {
    Composer.setDiagnosticStackTraceMode(
        if (isDebug) {
            ComposeStackTraceMode.SourceInformation
        } else {
            ComposeStackTraceMode.GroupKeys
        },
    )
}
