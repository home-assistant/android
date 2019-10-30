package io.homeassistant.android.webview

import io.homeassistant.android.io.homeassistant.android.api.Token


interface WebView {

    fun setupJavascriptInterface(token: Token)

    fun loadUrl(url: String)

}
