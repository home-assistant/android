package io.homeassistant.companion.android.testing.unit

import androidx.annotation.VisibleForTesting
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestPlan
import timber.log.Timber

/**
 * A custom Timber tree that logs messages to the console using `stderr`.
 *
 * This tree is specifically designed for use in testing environments where logs
 * need to be visible in the console during test execution for debugging.
 * By default, Timber does not print logs unless a tree is planted.
 * Additionally, JUnit captures `stdout`, so this tree directs logs to `stderr`
 * to ensure they are visible in the console.
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
        } else if (verbose) {
            System.err.println("[$tag] - $message")
        }
    }
}

/**
 * JUnit Platform listener that enables console Timber logging for the duration of the test JVM,
 * regardless of whether the test uses JUnit 4 (Vintage) or JUnit Jupiter.
 *
 * The tree is planted on the first test plan and never uprooted: the test JVM is short-lived,
 * and keeping the tree in place avoids redundant plant/uproot cycles when multiple test plans
 * run sequentially in the same worker.
 */
class ConsoleLogPlatformListener : TestExecutionListener {
    override fun testPlanExecutionStarted(testPlan: TestPlan) {
        if (!isConsoleLogEnabled()) {
            Timber.plant(ConsoleLogTree)
        }
        ConsoleLogTree.verbose = true
    }

    companion object {
        @VisibleForTesting
        fun isConsoleLogEnabled(): Boolean = Timber.forest().contains(ConsoleLogTree)
    }
}
