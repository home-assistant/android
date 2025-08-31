package io.homeassistant.companion.android.launch

interface LaunchPresenter {

    suspend fun onViewReady(serverUrlToOnboard: String? = null)

    suspend fun setSessionExpireMillis(value: Long)

    fun hasMultipleServers(): Boolean
}
