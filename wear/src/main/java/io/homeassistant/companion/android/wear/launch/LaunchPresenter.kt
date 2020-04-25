package io.homeassistant.companion.android.wear.launch

interface LaunchPresenter {
    fun onViewReady()
    suspend fun onRefresh()
    fun onFinish()
}