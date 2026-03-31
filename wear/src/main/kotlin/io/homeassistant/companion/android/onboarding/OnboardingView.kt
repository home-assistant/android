package io.homeassistant.companion.android.onboarding

import androidx.annotation.StringRes
import io.homeassistant.companion.android.database.server.TemporaryServer

interface OnboardingView {
    fun startIntegration(temporaryServer: TemporaryServer)

    fun onInstanceFound(instance: HomeAssistantInstance)
    fun onInstanceLost(instance: HomeAssistantInstance)

    fun showLoading()

    fun showContinueOnPhone()

    fun showError(@StringRes message: Int? = null)
}
