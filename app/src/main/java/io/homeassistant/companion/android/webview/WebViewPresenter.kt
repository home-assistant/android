package io.homeassistant.companion.android.webview

import android.content.Context

interface WebViewPresenter {

    fun onViewReady(path: String?)

    fun onGetExternalAuth(context: Context, callback: String, force: Boolean)

    fun checkSecurityVersion()

    fun onRevokeExternalAuth(callback: String)

    fun clearKnownUrls()

    fun isFullScreen(): Boolean

    fun isKeepScreenOnEnabled(): Boolean

    fun isPinchToZoomEnabled(): Boolean
    fun isWebViewDebugEnabled(): Boolean

    fun isAppLocked(): Boolean
    fun setAppActive(active: Boolean)

    fun isLockEnabled(): Boolean
    fun isAutoPlayVideoEnabled(): Boolean

    fun sessionTimeOut(): Int

    fun setSessionExpireMillis(value: Long)
    fun getSessionExpireMillis(): Long

    fun onFinish()

    fun isSsidUsed(): Boolean

    fun getAuthorizationHeader(): String

    suspend fun parseWebViewColor(webViewColor: String): Int
}
