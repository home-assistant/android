package io.homeassistant.companion.android.onboarding.integration

interface MobileAppIntegrationView {

    fun deviceRegistered()

    fun showLoading()

    fun showWarning()

    fun showError()
}
