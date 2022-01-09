package io.homeassistant.companion.android.onboarding.authentication

interface PasswordAuthenticationView {
    fun startIntegration()

    fun showMfa()

    fun showLoading()

    fun showError()
}
