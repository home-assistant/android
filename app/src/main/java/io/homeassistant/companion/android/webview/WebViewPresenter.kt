package io.homeassistant.companion.android.webview


interface WebViewPresenter {

    fun onViewReady()

    fun onGetExternalAuth(callback: String)

}
