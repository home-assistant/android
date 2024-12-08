package io.shpro.companion.android.webview.externalbus

import android.webkit.ValueCallback

data class ExternalBusMessage(
    val id: Any,
    val type: String,
    val command: String? = null,
    val success: Boolean? = null,
    val result: Any? = null,
    val error: Any? = null,
    val payload: Any? = null,
    val callback: ValueCallback<String>? = null
)
