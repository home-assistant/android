package io.homeassistant.companion.android.launch

interface LaunchView {
    fun displayWebview()

    fun displayOnBoarding(sessionConnected: Boolean, goToServer: String? = null)
}
