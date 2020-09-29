package io.homeassistant.companion.android.onboarding.integration

interface MobileAppIntegrationPresenter {
    fun onRegistrationAttempt(simple: Boolean, deviceName: String)
    fun onFinish()
}
