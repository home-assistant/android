package io.homeassistant.companion.android.onboarding.authentication

import io.homeassistant.companion.android.onboarding.HomeAssistantInstance

interface AuthenticationPresenter {

    fun onViewReady()

    fun onNextClicked(flowId: String, username: String, password: String)

    fun onFinish()
}