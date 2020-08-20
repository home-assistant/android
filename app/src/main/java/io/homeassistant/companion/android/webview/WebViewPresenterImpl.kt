package io.homeassistant.companion.android.webview

import android.graphics.Color
import android.net.Uri
import android.util.Log
import io.homeassistant.companion.android.domain.authentication.AuthenticationUseCase
import io.homeassistant.companion.android.domain.authentication.SessionState
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import io.homeassistant.companion.android.domain.integration.Panel
import io.homeassistant.companion.android.domain.url.UrlUseCase
import io.homeassistant.companion.android.util.UrlHandler
import java.net.URL
import java.util.regex.Matcher
import java.util.regex.Pattern
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
                val colorString = integrationUseCase.getThemeColor()
                // If color from theme found and if colorString is NOT #03A9F3.
                // This means a custom theme is set and the theme has a app-header-background-color defined
                // which we can use as navigation bar color and status bar color.
                // Attention: This is kind of an hack, as there is no way to check if a custom theme is set
                // or not in HA. Right now we check on the default color (#03A9F4), because
                // this color is given back if no theme is set. Always.
                // See here:
                // https://github.com/home-assistant/core/blob/ee64aafc3932ea0a7a76a33d1827db0c78fc0ed3/homeassistant/components/frontend/__init__.py#L362
                // TODO: Implement proper check for detecting if a custom theme is set or not in HA
                if (!colorString.isNullOrEmpty() && colorString != "#03A9F4") { // HA is using a custom theme
                    view.setStatusBarAndNavigationBarColor(parseColorWithRgb(colorString))
                }
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
                view.showError(isAuthenticationError = authenticationUseCase.getSessionState() == SessionState.ANONYMOUS)
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

    override fun getPanels(): Array<Panel> {
        return runBlocking {
            var panels = arrayOf<Panel>()
            try {
                panels = integrationUseCase.getPanels()
            } catch (e: Exception) {
                Log.e(TAG, "Issue getting panels.", e)
            }
            panels
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

    override fun isLockEnabled(): Boolean {
        return runBlocking {
            authenticationUseCase.isLockEnabled()
        }
    }

    override fun sessionTimeOut(): Int {
        return runBlocking {
            integrationUseCase.getSessionTimeOut()
        }
    }

    override fun setSessionExpireMillis(value: Long) {
        mainScope.launch {
            integrationUseCase.setSessionExpireMillis(value)
        }
    }

    override fun getSessionExpireMillis(): Long {
        return runBlocking {
            integrationUseCase.getSessionExpireMillis()
        }
    }

    override fun onFinish() {
        mainScope.cancel()
    }

    private fun parseColorWithRgb(colorString: String): Int {
        val c: Pattern = Pattern.compile("rgb *\\( *([0-9]+), *([0-9]+), *([0-9]+) *\\)")
        val m: Matcher = c.matcher(colorString)
        return if (m.matches()) {
            Color.rgb(
                m.group(1).toInt(),
                m.group(2).toInt(),
                m.group(3).toInt()
            )
        } else Color.parseColor(colorString)
    }
}
