package io.homeassistant.companion.android.webview

interface WebViewPresenter {

    fun onViewReady(path: String?)

    fun onGetExternalAuth(callback: String, force: Boolean)

    fun checkSecurityVersion()

    fun onRevokeExternalAuth(callback: String)

    fun clearKnownUrls()

    fun isFullScreen(): Boolean

    fun isKeepScreenOnEnabled(): Boolean

    fun isLockEnabled(): Boolean

    fun sessionTimeOut(): Int

    fun setSessionExpireMillis(value: Long)
    fun getSessionExpireMillis(): Long

    fun onFinish()

    fun isSsidUsed(): Boolean

    suspend fun getStatusBarAndNavigationBarColor(webViewColor: String): Int
}
