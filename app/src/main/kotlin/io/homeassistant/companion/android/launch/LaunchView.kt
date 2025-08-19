package io.homeassistant.companion.android.launch

import androidx.annotation.StringRes

interface LaunchView {
    suspend fun displayWebView()

    fun displayOnBoarding(sessionConnected: Boolean, serverUrlToOnboard: String? = null)

    fun displayAlertMessageDialog(@StringRes stringResId: Int)
    fun dismissDialog()
}
