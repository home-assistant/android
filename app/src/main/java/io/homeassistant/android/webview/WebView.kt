package io.homeassistant.android.webview

import io.homeassistant.android.io.homeassistant.android.api.Token


interface WebView {

    fun loadUrl(url: String)

    fun setToken(callback: String, token: Token)

}
