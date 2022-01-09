package io.homeassistant.companion.android.onboarding.authentication

interface AuthenticationView {
    fun startIntegration()

    fun showMfa()

    fun showLoading()

    fun showError()
}
