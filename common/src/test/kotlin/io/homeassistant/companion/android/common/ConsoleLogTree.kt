package io.homeassistant.companion.android.common

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
 *
 * ### Example:
 * ```kotlin
 * Timber.plant(ConsoleLogTree)
 * ConsoleLogTree.verbose = true
 * Timber.d("Debug message")
 * Timber.e(Exception("Test exception"), "Error message")
 * ```
 */
object ConsoleLogTree : Timber.DebugTree() {
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
