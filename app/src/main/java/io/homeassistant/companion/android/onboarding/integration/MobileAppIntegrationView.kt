package io.homeassistant.companion.android.onboarding.integration

interface MobileAppIntegrationView {

    fun deviceRegistered()

    fun registrationSkipped()

    fun showLoading()

    fun showError()
}
