package io.homeassistant.companion.android.onboarding.manual_setup

interface ManualSetupView {
    fun startAuthentication(flowId: String)

    fun showLoading()

    fun showError()
}
