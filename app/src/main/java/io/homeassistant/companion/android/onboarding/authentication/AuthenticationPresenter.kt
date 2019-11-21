package io.homeassistant.companion.android.onboarding.authentication

interface AuthenticationPresenter {

    fun onViewReady()

    fun onRedirectUrl(redirectUrl: String): Boolean

    fun onFinish()
}
