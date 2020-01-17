package io.homeassistant.companion.android.webview

interface WebViewPresenter {

    fun onViewReady()

    fun onGetExternalAuth(callback: String)

    fun onRevokeExternalAuth(callback: String)

    fun clearKnownUrls()

    fun isFS(): Boolean

    fun onFinish()
}
