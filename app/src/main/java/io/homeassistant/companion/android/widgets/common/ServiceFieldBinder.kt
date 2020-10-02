package io.homeassistant.companion.android.widgets.common

data class ServiceFieldBinder(
    val service: String,
    val field: String,
    var value: Any? = null
)
