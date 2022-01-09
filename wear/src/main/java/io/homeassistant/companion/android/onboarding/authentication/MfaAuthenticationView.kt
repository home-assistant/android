package io.homeassistant.companion.android.onboarding.authentication

interface MfaAuthenticationView {
    fun startIntegration()

    fun showLoading()

    fun showError()
}
