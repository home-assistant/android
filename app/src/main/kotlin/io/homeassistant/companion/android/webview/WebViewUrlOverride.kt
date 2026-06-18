package io.homeassistant.companion.android.webview

import android.net.Uri
import io.homeassistant.companion.android.util.hasSameOrigin

internal fun shouldOpenInExternalBrowser(currentUrl: String?, targetUrl: Uri): Boolean {
    if (!targetUrl.isHttpOrHttpsWithHost()) return true

    return !targetUrl.hasSameOrigin(currentUrl)
}

private fun Uri.isHttpOrHttpsWithHost(): Boolean {
    return host != null &&
        (scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true))
}
