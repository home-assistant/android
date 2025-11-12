package io.homeassistant.companion.android.testing.unit

import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import timber.log.Timber

/**
 * A custom Timber tree that logs messages to the console using `stderr`.
 *
 * This tree is specifically designed for use in testing environments where logs
 * need to be visible in the console during test execution for debugging.
 * By default, Timber does not print logs unless a tree is planted.
 * Additionally, JUnit captures `stdout`, so this tree directs logs to `stderr`
 * to ensure they are visible in the console.
 *
 * ### Features:
 * - Logs messages and exceptions to `stderr`.
 * - Supports a `verbose` mode to control whether logs without exception are printed.
 */
private object ConsoleLogTree : Timber.DebugTree() {
    /**
     * Controls whether logs without exception are printed to the console.
     * When `true`, all logs are printed. When `false`, only logs with exceptions are printed.
     */
    var verbose = false

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (t != null) {
            System.err.println("[$tag] - $message - ${t.stackTraceToString()}")
        } else {
            if (verbose) {
                System.err.println("[$tag] - $message")
            } else {
                // no-op
            }
        }
    }
}

/**
 * A JUnit 4 test rule that automatically sets up and tears down [ConsoleLogTree] for each test.
 *
 * This rule plants [ConsoleLogTree] with verbose mode enabled before each test starts
 * and removes it after the test finishes. This ensures that Timber logs are visible
 * in the console during test execution without requiring manual setup in each test.
 *
 * ### Usage:
 * ```kotlin
 * class MyTest {
 *     @get:Rule
 *     val consoleLogRule = ConsoleLogRule()
 *
 *     @Test
 *     fun myTest() {
 *         Timber.d("This will be visible in the console")
 *     }
 * }
 * ```
 *
 * @see ConsoleLogExtension for JUnit Jupiter tests
 */
class ConsoleLogRule : TestWatcher() {
    override fun starting(description: Description?) {
        super.starting(description)
        Timber.plant(ConsoleLogTree)
        ConsoleLogTree.verbose = true
    }

    override fun finished(description: Description?) {
        super.finished(description)
        Timber.uproot(ConsoleLogTree)
    }
}

/**
 * A JUnit Jupiter extension that automatically sets up and tears down [ConsoleLogTree]
 * for all tests in a test class.
 *
 * This extension plants [ConsoleLogTree] with verbose mode enabled before all tests in the class
 * start and removes it after all tests finish. This ensures that Timber logs are visible in the
 * console during test execution without requiring manual setup in each test.
 *
 * ### Usage:
 * ```kotlin
 * @ExtendWith(ConsoleLogExtension::class)
 * class MyTest {
 *     @Test
 *     fun myTest() {
 *         Timber.d("This will be visible in the console")
 *     }
 * }
 * ```
 *
 * @see ConsoleLogRule for JUnit 4 tests
 */
class ConsoleLogExtension :
    BeforeAllCallback,
    AfterAllCallback {
    override fun beforeAll(context: ExtensionContext) {
        Timber.plant(ConsoleLogTree)
        ConsoleLogTree.verbose = true
    }

    override fun afterAll(context: ExtensionContext) {
        Timber.uproot(ConsoleLogTree)
    }
}
