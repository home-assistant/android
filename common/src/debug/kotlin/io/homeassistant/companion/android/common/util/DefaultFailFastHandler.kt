package io.homeassistant.companion.android.common.util

import kotlin.system.exitProcess
import timber.log.Timber

private const val HEADER = """
        ████████████████████████████████████████████████████████████████
        ██                                                            ██
        ██           !!! CRITICAL FAILURE: FAIL-FAST !!!              ██
        ██                                                            ██
        ████████████████████████████████████████████████████████████████
        
        An unrecoverable error has occurred, and the FailFast mechanism
        has been triggered. The application cannot continue and will now exit.

        ACTION REQUIRED: This error must be investigated and resolved.
        Review the accompanying stack trace for details."""

private const val SEPARATOR = """----------------------------------------------------------------"""

object DefaultFailFastHandler : FailFastHandler {
    override fun handleException(exception: Exception, additionalMessage: String?) {
        Timber.e(
            exception,
            buildString {
                appendLine(HEADER.trimIndent())
                appendLine(SEPARATOR)
                additionalMessage?.let {
                    appendLine()
                    appendLine(it)
                    appendLine(SEPARATOR)
                    appendLine()
                }
            }
        )
        exitProcess(1)
    }
}
