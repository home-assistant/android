package io.homeassistant.companion.android.webview.externalbus

import android.webkit.ValueCallback

open class ExternalBusMessage(
    val id: Any?,
    val type: String,
    val command: String? = null,
    val success: Boolean? = null,
    val result: Any? = null,
    val error: Any? = null,
    val payload: Any? = null,
    val callback: ValueCallback<String>? = null,
)

class NavigateTo(path: String, replace: Boolean = false) :
    ExternalBusMessage(
        id = -1,
        type = "command",
        command = "navigate",
        payload = mapOf(
            "path" to path,
            "options" to mapOf(
                "replace" to replace,
            ),
        ),
    )
