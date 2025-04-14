package io.homeassistant.companion.android.common

import timber.log.Timber

object ConsoleLogTree : Timber.DebugTree() {
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
