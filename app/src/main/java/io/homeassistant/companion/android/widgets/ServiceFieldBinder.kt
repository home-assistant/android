package io.homeassistant.companion.android.widgets

data class ServiceFieldBinder(
    val service: String,
    val field: String,
    var value: Any? = null
)
