package io.homeassistant.companion.android.wear.launch

interface LaunchPresenter {
    fun onViewReady()
    fun onRefresh()
    fun onFinish()
}