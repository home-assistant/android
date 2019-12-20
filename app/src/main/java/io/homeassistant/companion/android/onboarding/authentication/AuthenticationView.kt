package io.homeassistant.companion.android.onboarding.authentication

interface AuthenticationView {

    fun loadUrl(url: String)

    fun openWebview()
}
