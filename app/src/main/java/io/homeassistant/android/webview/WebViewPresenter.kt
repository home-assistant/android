package io.homeassistant.android.webview


interface WebViewPresenter {

    fun onViewReady()

    fun onGetExternalAuth(callback: String)

}
