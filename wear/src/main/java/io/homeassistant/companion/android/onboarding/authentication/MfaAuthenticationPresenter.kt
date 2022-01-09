package io.homeassistant.companion.android.onboarding.authentication

interface MfaAuthenticationPresenter {

    fun onNextClicked(flowId: String, code: String)

    fun onFinish()
}
