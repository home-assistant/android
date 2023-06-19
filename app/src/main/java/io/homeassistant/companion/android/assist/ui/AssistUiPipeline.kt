package io.homeassistant.companion.android.assist.ui

data class AssistUiPipeline(
    val serverId: Int,
    val serverName: String,
    val id: String,
    val name: String,
    val attributionName: String? = null,
    val attributionUrl: String? = null
)
