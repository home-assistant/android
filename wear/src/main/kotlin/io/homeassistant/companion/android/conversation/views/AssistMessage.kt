package io.homeassistant.companion.android.conversation.views

private const val PLACEHOLDER = "…"

data class AssistMessage(val message: String, val isInput: Boolean, val isError: Boolean = false) {
    val isPlaceholder: Boolean
        get() = message == PLACEHOLDER

    companion object {
        fun placeholder(isInput: Boolean): AssistMessage = AssistMessage(PLACEHOLDER, isInput)
    }
}
