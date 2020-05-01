package io.homeassistant.companion.android.wear.launch

import androidx.annotation.StringRes

interface LaunchView {
    fun showProgressBar(show: Boolean)
    fun setStateInfo(message: Int?)
    fun showActionButton(message: Int?, icon: Int? = null, action: (() -> Unit)? = null)

    fun displayUnreachable()
    fun displayRetryActionButton(@StringRes stateMessage: Int)
    fun displayInactiveSession()
    fun displayNextScreen()
}