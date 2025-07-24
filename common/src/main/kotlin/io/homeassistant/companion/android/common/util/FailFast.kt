package io.homeassistant.companion.android.common.util

import io.homeassistant.companion.android.common.util.FailFast.setHandler
import kotlin.coroutines.cancellation.CancellationException

/**
 * A handler for [FailFast] exceptions.
 *
 * Implement this interface to define a custom handler if you want to do something different than [DefaultFailFastHandler].
 * Don't forget to register the handler in [FailFast.setHandler].
 */
fun interface FailFastHandler {
    fun handleException(throwable: Throwable, additionalMessage: String?)
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

    /**
     * Sets a custom [FailFastHandler] to handle exceptions.
     *
     * This method allows you to define how FailFast should handle exceptions
     * by providing your own implementation of the [FailFastHandler] interface.
     *
     * @param handler The [FailFastHandler] to use for handling exceptions.
     */
    fun setHandler(handler: FailFastHandler) {
        this.handler = handler
    }

    /**
     * Triggers the configured [FailFastHandler] with a custom message.
     *
     * This method allows to manually trigger the [FailFastHandler] by providing a specific
     * message. A [FailFastException] is created with the provided `message` and passed to the
     * [FailFastHandler], along with an optional `additionalMessage`.
     *
     * @param message A lambda function that returns the message for the [FailFastException].
     */
    fun fail(message: () -> String) {
        handler.handleException(FailFastException(message()), null)
    }

    /**
     * Triggers the configured [FailFastHandler] with a given [Throwable].
     *
     * This method allows to manually trigger the [FailFastHandler] by providing a specific
     * [Throwable]. The provided `throwable` is passed directly to the [FailFastHandler], to keep
     * the original stack trace.
     *
     * @param throwable The [Throwable] to be handled.
     */
    fun failWith(throwable: Throwable) {
        handler.handleException(throwable, null)
    }

    /**
     * Checks a condition and triggers the configured [FailFastHandler] if the condition is true.
     *
     * This method is used to assert conditions that should not occur during normal execution.
     * If the `condition` evaluates to `true`, a [FailFastException] is created with the provided
     * `message` and passed to the [FailFastHandler].
     *
     * @param condition The boolean condition to check. If true, the handler is triggered.
     * @param message A lambda function that returns the message for the [FailFastException].
     *                This is evaluated only if the condition is true.
     */
    fun failWhen(condition: Boolean, message: () -> String) {
        if (condition) {
            fail(message)
        }
    }

    /**
     * Executes a block of code and triggers the configured [FailFastHandler] if an exception occurs.
     *
     * This method is a convenience wrapper around a try-catch block. If an exception is caught
     * during the execution of the `block`, a [FailFastException] is created wrapping the original
     * exception and passed to the [FailFastHandler] along with an optional `message`.
     *
     * @param message A lambda function that returns an optional additional message for the handler.
     *                Defaults to a lambda returning `null`.
     * @param block The block of code to execute.
     */
    fun failOnCatch(message: () -> String? = { null }, block: () -> Unit) {
        failOnCatch(message, Unit, block)
    }

    /**
     * Executes a block of code that returns a value and triggers the configured [FailFastHandler] if an exception occurs,
     * returning a fallback value.
     *
     * This method is similar to the other `failOnCatch` but is designed for blocks of code that return a value.
     * If an exception is caught during the execution of the `block`, a [FailFastException] is created
     * wrapping the original exception and passed to the [FailFastHandler] along with an optional `message`.
     * The `fallback` value is then returned if the handler did not kill the process.
     *
     * @param T The type of the value returned by the `block` and the `fallback` value.
     * @param message A lambda function that returns an optional additional message for the handler.
     *                Defaults to a lambda returning `null`.
     * @param fallback The value to return if an exception occurs.
     * @param block The block of code to execute, which is expected to return a value of type `T`.
     * @return The result of the `block` execution, or the `fallback` value if an exception occurs.
     */
    fun <T> failOnCatch(message: () -> String? = { null }, fallback: T, block: () -> T): T {
        return try {
            block()
        } catch (e: Throwable) {
            handler.handleException(FailFastException(e), message())
            fallback
        }
    }

    /**
     * Executes a suspending block of code that returns a value and triggers the configured [FailFastHandler] if an exception occurs,
     * returning a fallback value.
     *
     * This method is similar to `failOnCatch` but is designed for suspending blocks of code that return a value.
     * If an exception (other than [CancellationException]) is caught during the execution of the `block`, a [FailFastException] is created
     * wrapping the original exception and passed to the [FailFastHandler] along with an optional `message`.
     * The `fallback` value is then returned if the handler did not kill the process.
     * [CancellationException]s are re-thrown as they are considered a normal part of coroutine behavior.
     *
     * @param T The type of the value returned by the `block` and the `fallback` value.
     * @param message A lambda function that returns an optional additional message for the handler.
     *                Defaults to a lambda returning `null`.
     * @param fallback The value to return if an exception occurs.
     * @param block The suspending block of code to execute, which is expected to return a value of type `T`.
     * @return The result of the `block` execution, or the `fallback` value if an exception occurs.
     * @throws CancellationException if the coroutine is cancelled during the execution of the `block`.
     */
    suspend fun <T> failOnCatchSuspend(message: () -> String? = { null }, fallback: T, block: suspend () -> T): T {
        return try {
            block()
        } catch (e: CancellationException) {
            // re-throw in case of cancellation since it's a normal behavior
            throw e
        } catch (e: Throwable) {
            handler.handleException(FailFastException(e), message())
            fallback
        }
    }
}
