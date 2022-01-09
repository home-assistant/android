package io.homeassistant.companion.android.onboarding.authentication

interface PasswordAuthenticationPresenter {

    fun onNextClicked(flowId: String, username: String?, password: String?, code: String?)

    fun onFinish()
}
