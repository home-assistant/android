package io.homeassistant.companion.android.onboarding.authentication

interface PasswordAuthenticationView {
    fun startIntegration()

    fun startMfa(flowId: String)

    fun showLoading()

    fun showError()
}
