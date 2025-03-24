package io.homeassistant.companion.android.onboarding

import androidx.annotation.StringRes

interface OnboardingView {
    fun startIntegration(serverId: Int)

    fun onInstanceFound(instance: HomeAssistantInstance)
    fun onInstanceLost(instance: HomeAssistantInstance)

    fun showLoading()

    fun showContinueOnPhone()

    fun showError(@StringRes message: Int? = null)
}
