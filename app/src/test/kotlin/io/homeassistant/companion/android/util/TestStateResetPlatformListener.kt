package io.homeassistant.companion.android.util

import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.common.util.FailFastHandler
import io.homeassistant.companion.android.common.util.SdkVersion
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier
import org.robolectric.pluginapi.TestEnvironmentLifecyclePlugin

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
 * JUnit Jupiter tests in this module. Robolectric tests are covered by [TestStateResetRobolectricPlugin].
 */
class TestStateResetPlatformListener : TestExecutionListener {
    override fun executionStarted(testIdentifier: TestIdentifier) {
        if (testIdentifier.isTest) resetTestState()
    }
}

/**
 * Robolectric counterpart of [TestStateResetPlatformListener].
 *
 * Robolectric runs tests in a sandbox class loader holding its own copy of [FailFast] and [SdkVersion],
 * which the JUnit Platform listener never reaches. Robolectric loads this plugin inside the sandbox
 * and calls it before every test.
 *
 * Registered via the Robolectric `ServiceLoader` (`org.robolectric.pluginapi.TestEnvironmentLifecyclePlugin`).
 */
class TestStateResetRobolectricPlugin : TestEnvironmentLifecyclePlugin {
    override fun onSetupApplicationState() {
        resetTestState()
    }
}

private fun resetTestState() {
    FailFast.setHandler(TestFailFastHandler)
    SdkVersion.resetSdkInt()
}
