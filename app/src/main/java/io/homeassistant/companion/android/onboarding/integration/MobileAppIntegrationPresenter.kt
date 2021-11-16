package io.homeassistant.companion.android.onboarding.integration

interface MobileAppIntegrationPresenter {
    fun init(mobileAppIntegrationView: MobileAppIntegrationView)
    fun onRegistrationAttempt(simple: Boolean, deviceName: String)
    fun onFinish()
}
