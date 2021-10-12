package io.homeassistant.companion.android.onboarding.authentication

import android.net.http.SslError
import android.webkit.WebResourceError

interface AuthenticationView {

    fun loadUrl(url: String)

    fun showError(message: Int, sslError: SslError?, error: WebResourceError?)

    fun openWebview()
}
