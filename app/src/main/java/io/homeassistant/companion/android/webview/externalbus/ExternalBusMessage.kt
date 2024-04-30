package io.homeassistant.companion.android.webview.externalbus

import android.webkit.ValueCallback

data class ExternalBusMessage(
    val id: Any,
    val type: String,
    val success: Boolean,
    val result: Any? = null,
    val error: Any? = null,
    val payload: Any? = null,
    val callback: ValueCallback<String>? = null
)
