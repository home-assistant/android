package io.homeassistant.companion.android.wear.launch

interface LaunchView {
    fun showProgressBar(show: Boolean)
    fun setStateInfo(message: Int?)
    fun showActionButton(message: Int?, icon: Int? = null, action: (() -> Unit)? = null)

    fun displayUnreachable()
    fun displayInactiveSession()
    fun displayNextScreen()
}