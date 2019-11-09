package io.homeassistant.companion.android.webview

import android.net.Uri
import io.homeassistant.companion.android.domain.authentication.AuthenticationUseCase
import kotlinx.coroutines.*
import javax.inject.Inject


class WebViewPresenterImpl @Inject constructor(
    private val view: WebView,
    private val authenticationUseCase: AuthenticationUseCase
) : WebViewPresenter {

    companion object {
        private const val TAG = "WebViewPresenterImpl"
    }

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onViewReady() {
        mainScope.launch {
            val url = authenticationUseCase.getUrl() ?: throw IllegalStateException("Unable to display the webview if we do not have url")

            view.loadUrl(
                Uri.parse(url.toString())
                    .buildUpon()
                    .appendQueryParameter("external_auth", "1")
                    .build()
                    .toString()
            )
        }
    }

    override fun onGetExternalAuth(callback: String) {
        mainScope.launch {
            view.setExternalAuth(callback, authenticationUseCase.retrieveExternalAuthentication())
        }
    }

    override fun onFinish() {
        mainScope.cancel()
    }

}