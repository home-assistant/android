package io.homeassistant.companion.android.launch

interface LaunchPresenter {

    fun onViewReady()

    fun setSessionExpireMillis(value: Long)

    fun onFinish()
}
