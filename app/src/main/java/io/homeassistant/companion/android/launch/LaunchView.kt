package io.homeassistant.companion.android.launch

interface LaunchView {

    fun displayLockView()

    fun displayWebview()

    fun displayOnBoarding(sessionConnected: Boolean)
}
