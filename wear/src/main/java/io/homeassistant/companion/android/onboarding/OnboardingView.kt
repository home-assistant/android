package io.homeassistant.companion.android.onboarding

interface OnboardingView {
    fun startAuthentication(flowId: String)
    fun startManualSetup()
    fun startPhoneSignIn()

    fun onInstanceFound(instance: HomeAssistantInstance)
    fun onInstanceLost(instance: HomeAssistantInstance)

    fun showLoading()

    fun showError()
}
