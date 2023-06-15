package io.homeassistant.companion.android.assist.ui

data class AssistUiCurrentPipeline(
    val serverId: Int,
    val id: String,
    val attributionName: String?,
    val attributionUrl: String?
)
