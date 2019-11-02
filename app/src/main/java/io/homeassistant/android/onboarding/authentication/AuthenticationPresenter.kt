package io.homeassistant.android.onboarding.authentication


interface AuthenticationPresenter {

    fun initialize(url: String)

    fun onViewReady()

    fun onRedirectUrl(redirectUrl: String): Boolean

}