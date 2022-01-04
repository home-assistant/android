package io.homeassistant.companion.android.onboarding.authentication

interface AuthenticationView {
    fun startIntegration()

    fun showLoading()

    fun showError()
}
