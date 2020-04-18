package io.homeassistant.companion.android.wear.ui.launch

import android.os.Handler
import android.os.Looper
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.MessageEvent
import io.homeassistant.companion.android.domain.authentication.AuthenticationUseCase
import io.homeassistant.companion.android.domain.authentication.Session
import io.homeassistant.companion.android.domain.authentication.SessionState
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import io.homeassistant.companion.android.domain.url.UrlUseCase
import io.homeassistant.companion.android.wear.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class LaunchPresenterImpl @Inject constructor(
    private val view: LaunchView,
    private val authenticationUseCase: AuthenticationUseCase,
    private val integrationUseCase: IntegrationUseCase,
    private val urlUseCase: UrlUseCase
) : LaunchPresenter {

    companion object {
        const val CONFIG_PATH = "/config"

        private const val KEY_ACTIVE_SESSION = "activeSession"
        private const val KEY_URLS = "urls"
        private const val KEY_SSIDS = "ssids"
        private const val KEY_SESSION = "session"
    }

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private val handler = Handler(Looper.getMainLooper())
    private val delayedShow = Runnable {
        view.showProgressBar(false)
        view.showActionButton(R.string.retry, R.drawable.ic_reload) {
            view.showActionButton(null)
            mainScope.launch { onRefresh() }
        }
    }

    override fun onViewReady() {
        mainScope.launch {
            val sessionValid = authenticationUseCase.getSessionState(true) == SessionState.CONNECTED
            if (sessionValid && integrationUseCase.isRegistered()) {
                delay(1500) // Prevents flashing.
                view.displayNextScreen()
            } else {
                onRefresh()
            }
        }
    }

    override suspend fun onRefresh() {
        handler.removeCallbacks(delayedShow)
        view.showProgressBar(true)
        val nodeWithAppInstalled = withContext(Dispatchers.IO) { view.getNodeWithInstalledApp() }
        if (nodeWithAppInstalled == null) {
            view.displayUnreachable()
        } else {
            handler.postDelayed(delayedShow, 5000)
            view.sendMessage(nodeWithAppInstalled.id)
        }
    }

    override fun onMessageReceived(message: MessageEvent) {
        when (message.path) {
            CONFIG_PATH -> {
                handler.removeCallbacks(delayedShow)
                val dataMap = DataMap.fromByteArray(message.data)
                if (!dataMap.getBoolean(KEY_ACTIVE_SESSION)) {
                    view.displayInactiveSession()
                }
                mainScope.launch {
                    val sessionMap = dataMap.getDataMap(KEY_SESSION)
                    val session = Session(
                        accessToken = sessionMap.getString("access"),
                        expiresTimestamp = sessionMap.getLong("expires"),
                        refreshToken = sessionMap.getString("refresh"),
                        tokenType = sessionMap.getString("type")
                    )
                    authenticationUseCase.saveSession(session)

                    val ssids = dataMap.getStringArrayList(KEY_SSIDS).toSet()
                    urlUseCase.saveHomeWifiSsids(ssids)

                    val urlMap = dataMap.getDataMap(KEY_URLS)
                    val cloudUrl = urlMap.getString("cloudhook_url")
                    val remoteUrl = urlMap.getString("remote_url")
                    val localUrl = urlMap.getString("local_url")
                    val webhookId = urlMap.getString("webhook_id")
                    urlUseCase.saveRegistrationUrls(cloudUrl, remoteUrl, webhookId, localUrl)
                    view.displayNextScreen()
                }
            }
        }
    }

    override fun onFinish() {
        handler.removeCallbacks(delayedShow)
        mainScope.cancel()
    }
    
}