package io.homeassistant.companion.android.webview

interface WebViewPresenter {

    fun onViewReady(path: String?)

    fun onGetExternalAuth(callback: String, force: Boolean)

    fun onRevokeExternalAuth(callback: String)

    fun clearKnownUrls()

    fun isFullScreen(): Boolean

    fun onFinish()
}
