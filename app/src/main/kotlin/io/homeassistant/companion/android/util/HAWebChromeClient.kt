package io.homeassistant.companion.android.util

import android.webkit.PermissionRequest
import android.webkit.WebChromeClient

/**
 * Custom [WebChromeClient] for the Home Assistant frontend WebView.
 */
class HAWebChromeClient(
    private val onPermissionRequest: (PermissionRequest) -> Unit = {},
) : WebChromeClient() {

    override fun onPermissionRequest(request: PermissionRequest?) {
        request?.let { onPermissionRequest.invoke(it) }
    }
}
