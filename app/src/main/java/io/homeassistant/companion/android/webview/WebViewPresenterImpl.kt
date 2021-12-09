package io.homeassistant.companion.android.webview

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ActivityContext
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.authentication.SessionState
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.url.UrlRepository
import io.homeassistant.companion.android.util.UrlHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.regex.Matcher
import java.util.regex.Pattern
import javax.inject.Inject

class WebViewPresenterImpl @Inject constructor(
    @ActivityContext context: Context,
    private val urlUseCase: UrlRepository,
    private val authenticationUseCase: AuthenticationRepository,
    private val integrationUseCase: IntegrationRepository
) : WebViewPresenter {

    companion object {
        private const val TAG = "WebViewPresenterImpl"
    }

    private val view = context as WebView

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private var url: URL? = null

    override fun onViewReady(path: String?) {
        mainScope.launch {
            val oldUrl = url
            url = urlUseCase.getUrl(urlUseCase.isInternal() || urlUseCase.isPrioritizeInternal())

            if (path != null && !path.startsWith("entityId:")) {
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
        }
    }

    override fun checkSecurityVersion() {
        mainScope.launch {

            try {
                val version = integrationUseCase.getHomeAssistantVersion().split(".")
                if (version.size >= 3) {
                    val year = Integer.parseInt(version[0])
                    val month = Integer.parseInt(version[1])
                    val release = Integer.parseInt(version[2])
                    if (year < 2021 || (year == 2021 && month == 1 && release < 5)) {
                        if (integrationUseCase.shouldNotifySecurityWarning()) {
                            view.showError(WebView.ErrorType.SECURITY_WARNING)
                        } else {
                            Log.w(TAG, "Still not updated but have already notified.")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Issue getting version/notifying of security issue.", e)
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
                view.showError(
                    if (authenticationUseCase.getSessionState() == SessionState.ANONYMOUS)
                        WebView.ErrorType.AUTHENTICATION
                    else
                        WebView.ErrorType.TIMEOUT
                )
            }
        }
    }

    override fun onRevokeExternalAuth(callback: String) {
        mainScope.launch {
            try {
                authenticationUseCase.revokeSession()
                view.setExternalAuth("$callback(true)")
                view.relaunchApp()
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

    override fun isKeepScreenOnEnabled(): Boolean {
        return runBlocking {
            integrationUseCase.isKeepScreenOnEnabled()
        }
    }

    override fun isLockEnabled(): Boolean {
        return runBlocking {
            authenticationUseCase.isLockEnabled()
        }
    }

    override fun isAutoPlayVideoEnabled(): Boolean {
        return runBlocking {
            integrationUseCase.isAutoPlayVideoEnabled()
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

    override fun isSsidUsed(): Boolean {
        return runBlocking {
            urlUseCase.getHomeWifiSsids().isNotEmpty()
        }
    }

    override suspend fun parseWebViewColor(webViewColor: String): Int = withContext(Dispatchers.IO) {
        var color = 0

        Log.d(TAG, "Try getting color from webview color \"$webViewColor\".")
        if (webViewColor.isNotEmpty() && webViewColor != "null") {
            try {
                color = parseColorWithRgb(webViewColor)
                Log.i(TAG, "Found color $color.")
            } catch (e: Exception) {
                Log.w(TAG, "Could not get color from webview.", e)
            }
        } else {
            Log.w(TAG, "Could not get color from webview. Color \"$webViewColor\" is not a valid color.")
        }

        if (color == 0) {
            Log.w(TAG, "Couldn't get color.")
        }

        return@withContext color
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
