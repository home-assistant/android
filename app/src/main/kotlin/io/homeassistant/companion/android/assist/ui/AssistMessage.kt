package io.homeassistant.companion.android.assist.ui

data class AssistMessage(val message: String, val isInput: Boolean, val isError: Boolean = false)
