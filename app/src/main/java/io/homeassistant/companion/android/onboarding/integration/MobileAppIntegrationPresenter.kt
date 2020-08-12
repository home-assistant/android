package io.homeassistant.companion.android.onboarding.integration

interface MobileAppIntegrationPresenter {
    fun onRegistrationAttempt(simple: Boolean)
    fun onToggleZoneTracking(enabled: Boolean)
    fun onToggleBackgroundTracking(enabled: Boolean)
    fun onToggleCallTracking(enabled: Boolean)
    fun onFinish()
}
