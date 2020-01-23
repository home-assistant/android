package io.homeassistant.companion.android.onboarding.integration

interface MobileAppIntegrationPresenter {
    fun onRegistrationAttempt()
    fun onToggleZoneTracking(enabled: Boolean)
    fun onToggleBackgroundTracking(enabled: Boolean)
    fun onFinish()
}
