package io.homeassistant.companion.android.common.util

import kotlin.system.exitProcess
import timber.log.Timber

private const val HEADER = """
        ██████████████████████
        !!! CRITICAL FAILURE: FAIL-FAST !!!
        ██████████████████████
"""

private const val SEPARATOR = """----------------------------------------------------------------"""

object CrashFailFastHandler : FailFastHandler {
    override fun handleException(throwable: Throwable, additionalMessage: String?) {
        Timber.e(
            throwable,
            buildString {
                appendLine(HEADER.trimIndent())
                appendLine()
                appendLine(
                    """
        An unrecoverable error has occurred, and the FailFast mechanism
        has been triggered. The application cannot continue and will now exit.
        
        ACTION REQUIRED: This error must be investigated and resolved.
        Review the accompanying stack trace for details.
                    """.trimIndent(),
                )
                appendLine(SEPARATOR)
                appendLine()
                additionalMessage?.let {
                    appendLine(it)
                    appendLine(SEPARATOR)
                    appendLine()
                }
            },
        )
        exitProcess(1)
    }
}

object LogOnlyFailFastHandler : FailFastHandler {
    override fun handleException(throwable: Throwable, additionalMessage: String?) {
        Timber.e(
            throwable,
            buildString {
                appendLine(HEADER.trimIndent())
                appendLine()
                appendLine(
                    """
            The error has been ignored to avoid a crash, but it should be handled.
            Please create a bug report at https://github.com/home-assistant/android/issues/new.
                    """.trimIndent(),
                )
                appendLine()
                additionalMessage?.let {
                    appendLine(it)
                    appendLine(SEPARATOR)
                    appendLine()
                }
            },
        )
        // no-op
    }
}
