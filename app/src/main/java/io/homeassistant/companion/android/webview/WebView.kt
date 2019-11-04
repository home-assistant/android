package io.homeassistant.companion.android.webview

import io.homeassistant.companion.android.api.Token


interface WebView {

    fun loadUrl(url: String)

    fun setToken(callback: String, token: Token)

}
