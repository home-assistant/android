package io.homeassistant.companion.android.util

import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.common.util.FailFastHandler

/**
 * [FailFastHandler] used during instrumentation tests that rethrows the captured exception as an
 * [AssertionError], so the JUnit runner reports it as a test failure instead of letting the
 * default handler crash the process.
 */
internal object TestFailFastHandler : FailFastHandler {
    override fun handleException(throwable: Throwable, additionalMessage: String?) {
        val message = buildString {
            append("Unhandled FailFast exception caught during test")
            if (!additionalMessage.isNullOrBlank()) {
                append(": ")
                append(additionalMessage)
            }
        }
        throw AssertionError(message, throwable)
    }
}

/**
 * Custom [AndroidJUnitRunner] that installs [TestFailFastHandler] before the instrumented
 * application is created.
 *
 * [FailFast] keeps a process-wide handler, and the application's `onCreate` (and the coroutines it
 * launches) run before any JUnit rule has a chance to override the default handler. Without this
 * runner, a [FailFast] triggered during application startup would terminate the process instead of
 * surfacing as a normal test failure.
 */
class HAAndroidJUnitRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle?) {
        FailFast.setHandler(TestFailFastHandler)
        super.onCreate(arguments)
    }
}
