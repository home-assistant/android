package io.homeassistant.companion.android.wear.launch

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.iid.FirebaseInstanceId
import io.homeassistant.companion.android.common.util.ProgressTimeLatch
import io.homeassistant.companion.android.domain.authentication.AuthenticationUseCase
import io.homeassistant.companion.android.domain.authentication.SessionState
import io.homeassistant.companion.android.domain.integration.DeviceRegistration
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import io.homeassistant.companion.android.domain.url.UrlUseCase
import io.homeassistant.companion.android.wear.BuildConfig
import io.homeassistant.companion.android.wear.R
import io.homeassistant.companion.android.wear.background.FailedSyncResult
import io.homeassistant.companion.android.wear.background.InActiveSessionSyncResult
import io.homeassistant.companion.android.wear.background.Result
import io.homeassistant.companion.android.wear.background.SettingsSyncCallback
import io.homeassistant.companion.android.wear.background.SettingsSyncManager
import io.homeassistant.companion.android.wear.background.SettingsUrl.*
import io.homeassistant.companion.android.wear.background.SuccessSyncResult
import io.homeassistant.companion.android.wear.background.SyncResult
import io.homeassistant.companion.android.util.extensions.await
import io.homeassistant.companion.android.util.extensions.catch
import io.homeassistant.companion.android.wear.background.capability.CapabilityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class LaunchPresenterImpl @Inject constructor(
    private val view: LaunchView,
    private val syncManager: SettingsSyncManager,
    private val capabilityManager: CapabilityManager,
    private val authenticationUseCase: AuthenticationUseCase,
    private val integrationUseCase: IntegrationUseCase,
    private val urlUseCase: UrlUseCase
) : LaunchPresenter, SettingsSyncCallback {

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main)
    private val progressLatch = ProgressTimeLatch(defaultValue = false, refreshingToggle = view::showProgressBar)

    private val handler = Handler(Looper.getMainLooper())
    private val delayedShow = Runnable {
        progressLatch.refreshing = false
        view.setStateInfo(R.string.ha_phone_app_not_reachable)
        view.showActionButton(R.string.retry, R.drawable.ic_reload) {
            view.showActionButton(null)
            view.setStateInfo(null)
            onRefresh()
        }
    }

    override fun onViewReady() {
        syncManager.syncCallback = this

        mainScope.launch {
            progressLatch.refreshing = true
            val sessionState = withContext(Dispatchers.IO) { authenticationUseCase.getSessionState() }
            val registered = integrationUseCase.isRegistered()
            if (sessionState == SessionState.CONNECTED && registered) {
                progressLatch.refreshing = false
                view.displayNextScreen()
            } else {
                onRefresh()
            }
        }
    }

    override fun onRefresh() {
        mainScope.launch {
            handler.removeCallbacks(delayedShow)
            progressLatch.refreshing = true
            val capabilityResult = withContext(Dispatchers.IO) { capabilityManager.getNodeWithInstalledApp() }
            if (capabilityResult == null || capabilityResult.result == Result.FAILURE) {
                progressLatch.refreshing = false
                view.displayUnreachable()
            } else if (capabilityResult.result == Result.NOT_NEARBY) {
                progressLatch.refreshing = false
                view.displayRetryActionButton(R.string.ha_state_handheld_not_nearby)
            } else {
                handler.postDelayed(delayedShow, 5000)

                val deviceId = capabilityResult.deviceNode.id
                val result = withContext(Dispatchers.IO) { syncManager.sendMessage(deviceId) }
                if (BuildConfig.DEBUG) {
                    Log.d("LaunchPresenter", "Send sync message result: $result")
                }
            }
        }
    }

    override fun onSyncResult(result: SyncResult) {
        handler.removeCallbacks(delayedShow)
        when (result) {
            is FailedSyncResult,
            is InActiveSessionSyncResult -> {
                val message =
                    if (result is FailedSyncResult) R.string.ha_state_failed_sync
                    else R.string.ha_state_session_inactive

                progressLatch.refreshing = false
                view.displayRetryActionButton(message)
            }
            is SuccessSyncResult -> mainScope.launch {
                val registeredDevice = withContext(Dispatchers.IO) {
                    saveSettings(result) && registerDevice()
                }
                progressLatch.refreshing = false
                if (registeredDevice) {
                    view.displayNextScreen()
                } else {
                    view.displayRetryActionButton(R.string.unable_to_register)
                }
            }
        }
    }

    private suspend fun saveSettings(result: SuccessSyncResult): Boolean {
        val urlMap = result.urls
        val webhookUrl = urlMap[WEBHOOK] ?: return false
        urlUseCase.saveRegistrationUrls(urlMap[CLOUDHOOK], urlMap[REMOTE], webhookUrl, urlMap[LOCAL])
        authenticationUseCase.saveSession(result.session)
        urlUseCase.saveHomeWifiSsids(result.ssids.toSet())
        return true
    }

    private suspend fun registerDevice(): Boolean {
        val token = catch { FirebaseInstanceId.getInstance().instanceId.await() } ?: return false
        val registration = DeviceRegistration(
            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            Build.MODEL ?: "UNKNOWN",
            token.token
        )
        return catch { integrationUseCase.registerDevice(registration) } != null
    }

    override fun onFinish() {
        syncManager.cancel()
        handler.removeCallbacks(delayedShow)
        mainScope.cancel()
    }
    
}