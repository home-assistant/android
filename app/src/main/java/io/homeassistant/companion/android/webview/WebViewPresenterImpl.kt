package io.homeassistant.companion.android.webview

import android.graphics.Color
import android.net.Uri
import android.util.Log
import io.homeassistant.companion.android.domain.authentication.AuthenticationUseCase
import io.homeassistant.companion.android.domain.authentication.SessionState
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import io.homeassistant.companion.android.domain.url.UrlUseCase
import io.homeassistant.companion.android.util.UrlHandler
import java.net.URL
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class WebViewPresenterImpl @Inject constructor(
    private val view: WebView,
    private val urlUseCase: UrlUseCase,
    private val authenticationUseCase: AuthenticationUseCase,
    private val integrationUseCase: IntegrationUseCase
) : WebViewPresenter {

    companion object {
        private const val TAG = "WebViewPresenterImpl"
    }

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private var url: URL? = null

    override fun onViewReady(path: String?) {
        mainScope.launch {
            val oldUrl = url
            url = urlUseCase.getUrl()

            if (path != null) {
                url = UrlHandler.handle(url, path)
            }

            /*
            We only want to cause the UI to reload if the URL that we need to load has changed.  An
            example of this would be opening the app on wifi with a local url then loosing wifi
            signal and reopening app.  Without this we would still be trying to use the internal
            url externally.
             */
            if (oldUrl?.host != url?.host) {
                view.loadUrl(
                    Uri.parse(url.toString())
                        .buildUpon()
                        .appendQueryParameter("external_auth", "1")
                        .build()
                        .toString()
                )
            }

            try {
                view.setStatusBarColor(Color.parseColor(integrationUseCase.getThemeColor()))
            } catch (e: Exception) {
                Log.e(TAG, "Issue getting/setting theme", e)
            }
        }
    }

    override fun onGetExternalAuth(callback: String, force: Boolean) {
        mainScope.launch {
            try {
                view.setExternalAuth("$callback(true, ${authenticationUseCase.retrieveExternalAuthentication(force)})")
            } catch (e: Exception) {
                Log.e(TAG, "Unable to retrieve external auth", e)
                view.setExternalAuth("$callback(false)")
                view.showError(authenticationUseCase.getSessionState() == SessionState.ANONYMOUS)
            }
        }
    }

    override fun onRevokeExternalAuth(callback: String) {
        mainScope.launch {
            try {
                authenticationUseCase.revokeSession()
                view.setExternalAuth("$callback(true)")
                view.openOnBoarding()
            } catch (e: Exception) {
                Log.e(TAG, "Unable to revoke session", e)
                view.setExternalAuth("$callback(false)")
            }
        }
    }

    override fun clearKnownUrls() {
        mainScope.launch {
            urlUseCase.saveUrl("", true)
            urlUseCase.saveUrl("", false)
        }
    }

    override fun isFullScreen(): Boolean {
        return runBlocking {
            integrationUseCase.isFullScreenEnabled()
        }
    }

    override fun onFinish() {
        mainScope.cancel()
    }
}
