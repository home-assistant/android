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
 * JUnit Platform listener that resets process-wide test state before every test.
 *
 * Both [FailFast] (its handler) and [SdkVersion] (its [SdkVersion.sdkInt]) are process-wide
 * singletons, so an override in one test would otherwise leak into subsequent tests. Resetting them
 * at the start of each test isolates tests from one another:
 * - [TestFailFastHandler] is installed so any [FailFast] failure surfaces as a normal test failure.
 * - [SdkVersion.resetSdkInt] restores the default SDK level (0 in plain JVM tests), so a test that
 *   reaches an [SdkVersion.isAtLeast] gate without setting the level fails fast instead of silently
 *   inheriting a value from an earlier test.
 *
 * Registered via the JUnit Platform `ServiceLoader`, so it applies to both JUnit 4 (Vintage) and
 * JUnit Jupiter tests in this module.
 */
class TestStateResetPlatformListener : TestExecutionListener {
    override fun executionStarted(testIdentifier: TestIdentifier) {
        if (testIdentifier.isTest) {
            FailFast.setHandler(TestFailFastHandler)
            SdkVersion.resetSdkInt()
        }
    }
}
