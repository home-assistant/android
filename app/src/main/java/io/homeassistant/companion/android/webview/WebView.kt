package io.homeassistant.companion.android.webview


interface WebView {

    fun loadUrl(url: String)

    fun setExternalAuth(callback: String, externalAuth: String)

}
