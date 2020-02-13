package io.homeassistant.companion.android.onboarding.authentication

interface AuthenticationView {

    fun loadUrl(url: String)

    fun showError(message: Int)

    fun openWebview()
}
