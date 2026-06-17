package io.homeassistant.companion.android.webview

import android.net.Uri
import io.homeassistant.companion.android.util.hasSameOrigin

internal fun shouldOpenInExternalBrowser(currentUrl: String?, targetUrl: Uri): Boolean {
    return !targetUrl.hasSameOrigin(currentUrl)
}
