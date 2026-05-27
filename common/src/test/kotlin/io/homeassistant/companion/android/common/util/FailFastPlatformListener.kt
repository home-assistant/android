package io.homeassistant.companion.android.common.util

import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier

/**
 * [FailFastHandler] used during unit tests that rethrows the captured exception as an
 * [AssertionError], so the JUnit runner reports it as a test failure instead of letting the
 * default handler crash the JVM.
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
 * JUnit Platform listener that installs [TestFailFastHandler] before every test.
 *
 * [FailFast] keeps a process-wide handler, so a test that overrides it would leak its override to
 * subsequent tests. Resetting the handler at the start of each test isolates them from one another
 * and ensures any [FailFast] failure is surfaced as a normal test failure.
 *
 * Registered via the JUnit Platform `ServiceLoader`, so it applies to both JUnit 4 (Vintage) and
 * JUnit Jupiter tests in this module.
 */
class FailFastPlatformListener : TestExecutionListener {
    override fun executionStarted(testIdentifier: TestIdentifier) {
        if (testIdentifier.isTest) {
            FailFast.setHandler(TestFailFastHandler)
        }
    }
}
