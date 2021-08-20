package io.homeassistant.companion.android.onboarding

interface OnboardingView {
    fun startAuthentication(flowId: String)
    fun startManualSetup()

    fun showLoading()

    fun showError()
}