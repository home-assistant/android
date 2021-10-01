package io.homeassistant.companion.android.home

interface HomePresenter {

    fun onViewReady()
    fun onLogoutClicked()
    fun onFinish()
}
