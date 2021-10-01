package io.homeassistant.companion.android.onboarding.integration

interface MobileAppIntegrationPresenter {
    fun onRegistrationAttempt(deviceName: String)
    fun onFinish()
}
