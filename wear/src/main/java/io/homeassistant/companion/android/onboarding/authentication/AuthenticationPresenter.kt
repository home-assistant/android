package io.homeassistant.companion.android.onboarding.authentication

interface AuthenticationPresenter {

    fun onNextClicked(flowId: String, username: String, password: String)

    fun onFinish()
}
