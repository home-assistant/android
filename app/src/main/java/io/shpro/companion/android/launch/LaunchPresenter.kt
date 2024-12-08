package io.shpro.companion.android.launch

interface LaunchPresenter {

    fun onViewReady()

    fun setSessionExpireMillis(value: Long)

    fun hasMultipleServers(): Boolean

    fun onFinish()
}
