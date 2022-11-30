package io.homeassistant.companion.android.webview

import android.content.Context
import android.content.IntentSender
import android.graphics.Color
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ActivityContext
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.authentication.SessionState
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.url.UrlRepository
import io.homeassistant.companion.android.common.util.DisabledLocationHandler
import io.homeassistant.companion.android.matter.MatterCommissioningRequest
import io.homeassistant.companion.android.matter.MatterRepository
import io.homeassistant.companion.android.util.UrlHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.net.SocketTimeoutException
import java.net.URL
import java.util.regex.Matcher
import java.util.regex.Pattern
import javax.inject.Inject
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import io.homeassistant.companion.android.common.R as commonR

class WebViewPresenterImpl @Inject constructor(
    @ActivityContext context: Context,
    private val urlUseCase: UrlRepository,
    private val authenticationUseCase: AuthenticationRepository,
    private val integrationUseCase: IntegrationRepository,
    private val matterUseCase: MatterRepository
) : WebViewPresenter {

    companion object {
        private const val TAG = "WebViewPresenterImpl"
    }

    private val view = context as WebView

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private var url: URL? = null

    private val _matterCommissioningStatus = MutableStateFlow(MatterCommissioningRequest.Status.NOT_STARTED)

    private var matterCommissioningIntentSender: IntentSender? = null

    override fun onViewReady(path: String?) {
        mainScope.launch {
            val oldUrl = url
            url = urlUseCase.getUrl(urlUseCase.isInternal() || (urlUseCase.isPrioritizeInternal() && !DisabledLocationHandler.isLocationEnabled(view as Context)))

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
                if (!integrationUseCase.isHomeAssistantVersionAtLeast(2021, 1, 5)) {
                    if (integrationUseCase.shouldNotifySecurityWarning()) {
                        view.showError(WebView.ErrorType.SECURITY_WARNING)
                    } else {
                        Log.w(TAG, "Still not updated but have already notified.")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Issue getting version/notifying of security issue.", e)
            }
        }
    }

    override fun onGetExternalAuth(context: Context, callback: String, force: Boolean) {
        mainScope.launch {
            try {
                view.setExternalAuth("$callback(true, ${authenticationUseCase.retrieveExternalAuthentication(force)})")
            } catch (e: Exception) {
                Log.e(TAG, "Unable to retrieve external auth", e)
                val anonymousSession = authenticationUseCase.getSessionState() == SessionState.ANONYMOUS
                view.setExternalAuth("$callback(false)")
                view.showError(
                    errorType = when {
                        anonymousSession -> WebView.ErrorType.AUTHENTICATION
                        e is SSLException || (e is SocketTimeoutException && e.suppressed.any { it is SSLException }) -> WebView.ErrorType.SSL
                        else -> WebView.ErrorType.TIMEOUT
                    },
                    description = when {
                        anonymousSession -> null
                        e is SSLHandshakeException || (e is SocketTimeoutException && e.suppressed.any { it is SSLHandshakeException }) -> context.getString(commonR.string.webview_error_FAILED_SSL_HANDSHAKE)
                        e is SSLException || (e is SocketTimeoutException && e.suppressed.any { it is SSLException }) -> context.getString(commonR.string.webview_error_SSL_INVALID)
                        else -> null
                    }
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
            urlUseCase.updateCloudUrls(null, null)
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

    override fun isPinchToZoomEnabled(): Boolean {
        return runBlocking {
            integrationUseCase.isPinchToZoomEnabled()
        }
    }

    override fun isWebViewDebugEnabled(): Boolean {
        return runBlocking {
            integrationUseCase.isWebViewDebugEnabled()
        }
    }

    override fun isAppLocked(): Boolean {
        return runBlocking {
            integrationUseCase.isAppLocked()
        }
    }

    override fun setAppActive(active: Boolean) {
        return runBlocking {
            integrationUseCase.setAppActive(active)
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

    override fun onFinish() {
        mainScope.cancel()
    }

    override fun isSsidUsed(): Boolean {
        return runBlocking {
            urlUseCase.getHomeWifiSsids().isNotEmpty()
        }
    }

    override fun getAuthorizationHeader(): String {
        return runBlocking {
            authenticationUseCase.buildBearerToken()
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

    override fun appCanCommissionMatterDevice(): Boolean = matterUseCase.appSupportsCommissioning()

    override fun startCommissioningMatterDevice(context: Context) {
        if (_matterCommissioningStatus.value != MatterCommissioningRequest.Status.REQUESTED) {
            _matterCommissioningStatus.tryEmit(MatterCommissioningRequest.Status.REQUESTED)

            matterUseCase.startNewCommissioningFlow(
                context,
                { intentSender ->
                    Log.d(TAG, "Matter commissioning is ready")
                    matterCommissioningIntentSender = intentSender
                    _matterCommissioningStatus.tryEmit(MatterCommissioningRequest.Status.IN_PROGRESS)
                },
                { e ->
                    Log.e(TAG, "Matter commissioning couldn't be prepared", e)
                    _matterCommissioningStatus.tryEmit(MatterCommissioningRequest.Status.ERROR)
                }
            )
        } // else already waiting for a result, don't send another request
    }

    override fun getMatterCommissioningStatusFlow(): Flow<MatterCommissioningRequest.Status> =
        _matterCommissioningStatus.asStateFlow()

    override fun getMatterCommissioningIntent(): IntentSender? {
        val intent = matterCommissioningIntentSender
        matterCommissioningIntentSender = null
        return intent
    }
}
