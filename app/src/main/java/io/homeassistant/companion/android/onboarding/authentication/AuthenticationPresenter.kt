package io.homeassistant.companion.android.onboarding.authentication

interface AuthenticationPresenter {

    fun init(authenticationView: AuthenticationView)

    fun onViewReady()

    fun onRedirectUrl(redirectUrl: String): Boolean

    fun onFinish()
}
