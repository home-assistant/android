package io.homeassistant.android.onboarding.authentication


interface AuthenticationView {

    fun loadUrl(url: String)

    fun openWebview(url: String)

}
