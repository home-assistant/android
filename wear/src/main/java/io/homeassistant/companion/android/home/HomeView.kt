package io.homeassistant.companion.android.home

interface HomeView {
    fun showHomeAssistantVersion(version: String)
    fun showEntitiesCount(count: Int)

    fun displayOnBoarding()
    fun displayMobileAppIntegration()
}
