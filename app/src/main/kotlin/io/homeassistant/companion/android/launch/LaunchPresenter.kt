package io.homeassistant.companion.android.launch

interface LaunchPresenter {

    fun onViewReady(serverUrlToOnboard: String? = null)

    fun setSessionExpireMillis(value: Long)

    fun hasMultipleServers(): Boolean

    fun onFinish()
}
