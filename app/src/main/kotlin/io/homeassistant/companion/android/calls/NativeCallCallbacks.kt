package io.homeassistant.companion.android.calls

/** Release call-owned closures even if Telecom retains a completed result receiver. */
internal class NativeCallCallbacks(onAnswer: suspend () -> Unit, onDisconnect: suspend () -> Unit) : AutoCloseable {
    private var answerHandler: (suspend () -> Unit)? = onAnswer
    private var disconnectHandler: (suspend () -> Unit)? = onDisconnect

    suspend fun answer() {
        answerHandler?.invoke()
    }

    suspend fun disconnect() {
        disconnectHandler?.invoke()
    }

    override fun close() {
        answerHandler = null
        disconnectHandler = null
    }
}
