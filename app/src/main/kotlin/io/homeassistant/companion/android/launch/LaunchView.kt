package io.homeassistant.companion.android.launch

interface LaunchView {
    suspend fun displayWebview()

    fun displayOnBoarding(sessionConnected: Boolean, serverUrlToOnboard: String? = null)
}
