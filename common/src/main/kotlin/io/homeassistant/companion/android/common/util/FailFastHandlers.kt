package io.homeassistant.companion.android.common.util

import kotlin.system.exitProcess
import timber.log.Timber

private const val HEADER = """
        ████████████████████████████████████████████████████████████████
        ██                                                            ██
        ██           !!! CRITICAL FAILURE: FAIL-FAST !!!              ██
        ██                                                            ██
        ████████████████████████████████████████████████████████████████
"""

private const val SEPARATOR = """----------------------------------------------------------------"""

object CrashFailFastHandler : FailFastHandler {
    override fun handleException(exception: Exception, additionalMessage: String?) {
        Timber.e(
            exception,
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
                additionalMessage?.let {
                    appendLine()
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
    override fun handleException(exception: Exception, additionalMessage: String?) {
        Timber.e(
            exception,
            buildString {
                appendLine(HEADER.trimIndent())
                appendLine()
                appendLine(
                    """
            The exception is ignored to avoid a crash but it should be handled
            if you see this please open an issue on github https://github.com/home-assistant/android/issues/new/choose
                    """.trimIndent(),
                )
                additionalMessage?.let {
                    appendLine()
                    appendLine(it)
                    appendLine(SEPARATOR)
                    appendLine()
                }
            },
        )
        // no-op
    }
}
