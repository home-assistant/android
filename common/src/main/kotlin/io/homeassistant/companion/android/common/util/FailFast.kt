package io.homeassistant.companion.android.common.util

import io.homeassistant.companion.android.common.util.FailFast.setHandler

/**
 * A handler for [FailFast] exceptions.
 *
 * Implement this interface to define a custom handler if you want to do something different than [DefaultFailFastHandler].
 * Don't forget to register the handler in [FailFast.setHandler].
 */
interface FailFastHandler {
    fun handleException(exception: Exception, additionalMessage: String? = null)
}

private class FailFastException : Exception {
    constructor(message: String) : super(message)
    constructor(exception: Throwable) : super(exception)

    init {
        // We remove any reference to FailFast from the stack trace to make it easier to find the root cause
        stackTrace = stackTrace.filterNot { it.className == FailFast::class.java.name }.toTypedArray()
    }
}

/**
 * A utility object for implementing the "fail fast" [principle](https://en.wikipedia.org/wiki/Fail-fast_system).
 *
 * This object provides methods to check conditions and handle exceptions,
 * allowing to identify and address issues sooner in the development lifecycle.
 *
 * By default, it uses [DefaultFailFastHandler] to log exceptions. This behavior can be
 * customized by providing a different [FailFastHandler] implementation using [setHandler].
 *
 * [DefaultFailFastHandler] behavior is different based on the build target debug or release.
 */
object FailFast {
    private var handler: FailFastHandler = DefaultFailFastHandler

    fun setHandler(handler: FailFastHandler) {
        this.handler = handler
    }

    fun failWhen(condition: Boolean, message: () -> String) {
        if (condition) {
            handler.handleException(FailFastException(message()))
        }
    }

    fun failOnCatch(message: () -> String? = { null }, block: () -> Unit) {
        failOnCatch<Unit>(message, Unit, block)
    }

    fun <T> failOnCatch(message: () -> String? = { null }, fallback: T, block: () -> T): T {
        return try {
            block()
        } catch (e: Throwable) {
            handler.handleException(FailFastException(e), message())
            fallback
        }
    }
}
