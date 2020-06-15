package io.homeassistant.companion.android.webview

import io.homeassistant.companion.android.domain.integration.Panel

interface WebViewPresenter {

    fun onViewReady(path: String?)

    fun onGetExternalAuth(callback: String, force: Boolean)

    fun onRevokeExternalAuth(callback: String)

    fun getPanels(): Array<Panel>

    fun clearKnownUrls()

    fun isFullScreen(): Boolean

    fun isLockEnabled(): Boolean

    fun sessionTimeOut(): Int

    fun setSessionExpireMillis(value: Long)
    fun getSessionExpireMillis(): Long

    fun onFinish()
}
