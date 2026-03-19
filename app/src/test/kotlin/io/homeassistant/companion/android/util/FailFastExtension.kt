package io.homeassistant.companion.android.util

import io.homeassistant.companion.android.common.util.DefaultFailFastHandler
import io.homeassistant.companion.android.common.util.FailFast
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * A JUnit Jupiter extension that resets the [FailFast] handler to [DefaultFailFastHandler]
 * after each test.
 *
 * Tests that override the [FailFast] handler (e.g., to capture exceptions or suppress crashes)
 * must ensure the handler is restored after the test to avoid leaking state to other tests.
 * This extension automates that cleanup.
 *
 * ### Usage:
 * ```kotlin
 * @ExtendWith(FailFastExtension::class)
 * class MyTest {
 *     @Test
 *     fun myTest() {
 *          FailFast.setHandler { throwable, _ -> /* custom handler */ }
 *         // FailFast handler is automatically reset after each test
 *     }
 * }
 * ```
 *
 * @see FailFastRule for JUnit 4 tests
 */
class FailFastExtension : AfterEachCallback {
    override fun afterEach(context: ExtensionContext) {
        FailFast.setHandler(DefaultFailFastHandler)
    }
}

/**
 * A JUnit 4 test rule that resets the [FailFast] handler to [DefaultFailFastHandler]
 * after each test.
 *
 * ### Usage:
 * ```kotlin
 * class MyTest {
 *     @get:Rule
 *     val failFastRule = FailFastRule()
 *
 *     @Test
 *     fun myTest() {
 *          FailFast.setHandler { throwable, _ -> /* custom handler */ }
 *         // FailFast handler is automatically reset after each test
 *     }
 * }
 * ```
 *
 * @see FailFastExtension for JUnit Jupiter tests
 */
class FailFastRule : TestWatcher() {
    override fun finished(description: Description?) {
        FailFast.setHandler(DefaultFailFastHandler)
    }
}
