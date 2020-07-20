package io.homeassistant.companion.android.onboarding.integration

interface MobileAppIntegrationPresenter {
    fun onRegistrationAttempt(includeFirebase: Boolean = true)
    fun onToggleZoneTracking(enabled: Boolean)
    fun onToggleBackgroundTracking(enabled: Boolean)
    fun onFinish()
}
