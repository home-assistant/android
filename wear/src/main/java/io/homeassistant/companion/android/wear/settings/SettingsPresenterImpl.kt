package io.homeassistant.companion.android.wear.settings

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.wear.activity.ConfirmationActivity.FAILURE_ANIMATION
import androidx.wear.activity.ConfirmationActivity.SUCCESS_ANIMATION
import com.google.firebase.iid.FirebaseInstanceId
import io.homeassistant.companion.android.common.util.ProgressTimeLatch
import io.homeassistant.companion.android.domain.authentication.AuthenticationUseCase
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import io.homeassistant.companion.android.domain.url.UrlUseCase
import io.homeassistant.companion.android.wear.BuildConfig
import io.homeassistant.companion.android.wear.R
import io.homeassistant.companion.android.wear.background.FailedSyncResult
import io.homeassistant.companion.android.wear.background.InActiveSessionSyncResult
import io.homeassistant.companion.android.wear.background.Result
import io.homeassistant.companion.android.wear.background.SettingsSyncCallback
import io.homeassistant.companion.android.wear.background.SettingsSyncManager
import io.homeassistant.companion.android.wear.background.SettingsUrl
import io.homeassistant.companion.android.wear.background.SuccessSyncResult
import io.homeassistant.companion.android.wear.background.SyncResult
import io.homeassistant.companion.android.wear.util.extensions.await
import io.homeassistant.companion.android.wear.util.extensions.catch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

class SettingsPresenterImpl @Inject constructor(
    private val view: SettingsView,
    private val syncManager: SettingsSyncManager,
    private val authenticationUseCase: AuthenticationUseCase,
    private val integrationUseCase: IntegrationUseCase,
    private val urlUseCase: UrlUseCase
) : SettingsPresenter, SettingsSyncCallback {

    private val mainScope = CoroutineScope(Dispatchers.Main + Job())
    private val progressLatch = ProgressTimeLatch(defaultValue = false, refreshingToggle = view::displaySyncInProgress)
    private val isLoading = AtomicBoolean(false)

    private val handler = Handler(Looper.getMainLooper())
    private val delayedShow = Runnable {
        view.showConfirmed(FAILURE_ANIMATION, R.string.ha_phone_app_not_reachable_short)
    }

    override fun onViewReady() {
        syncManager.syncCallback = this
    }

    override fun syncSettings() {
        if (isLoading.get()) {
            return
        }
        isLoading.set(true)

        mainScope.launch {
            progressLatch.refreshing = true
            val capabilityResult = withContext(Dispatchers.IO) { syncManager.getNodeWithInstalledApp() }
            if (capabilityResult == null || capabilityResult.result == Result.FAILURE) {
                isLoading.set(false)
                progressLatch.refreshing = false
                view.showConfirmed(FAILURE_ANIMATION, R.string.ha_phone_app_not_reachable)
            } else if (capabilityResult.result == Result.NOT_NEARBY) {
                isLoading.set(false)
                progressLatch.refreshing = false
                view.showConfirmed(FAILURE_ANIMATION, R.string.ha_state_handheld_not_nearby)
            } else {
                val deviceId = capabilityResult.deviceNode.id
                val result = withContext(Dispatchers.IO) { syncManager.sendMessage(deviceId) }
                handler.postDelayed(delayedShow, 5000)
                if (BuildConfig.DEBUG) {
                    Log.d("SettingsPresenter", "Send sync message result: $result")
                }
            }
        }
    }

    override fun onSyncResult(result: SyncResult) {
        handler.removeCallbacks(delayedShow)
        when (result) {
            is FailedSyncResult -> {
                isLoading.set(false)
                progressLatch.refreshing = false
                view.showConfirmed(FAILURE_ANIMATION, R.string.ha_state_session_inactive)
            }
            is InActiveSessionSyncResult -> {
                isLoading.set(false)
                progressLatch.refreshing = false
                view.showConfirmed(FAILURE_ANIMATION, R.string.ha_state_session_inactive)
            }
            is SuccessSyncResult -> mainScope.launch {
                val registeredDevice = withContext(Dispatchers.IO) {
                    saveSettings(result) && updateDevice()
                }
                isLoading.set(false)
                progressLatch.refreshing = false
                if (registeredDevice) {
                    view.showConfirmed(SUCCESS_ANIMATION, R.string.ha_settings_synced)
                } else {
                    view.showConfirmed(FAILURE_ANIMATION, R.string.error_with_registration)
                }
            }
        }
    }

    private suspend fun saveSettings(result: SuccessSyncResult): Boolean {
        val urlMap = result.urls
        val webhookUrl = urlMap[SettingsUrl.WEBHOOK] ?: return false
        urlUseCase.saveRegistrationUrls(urlMap[SettingsUrl.CLOUDHOOK], urlMap[SettingsUrl.REMOTE], webhookUrl, urlMap[SettingsUrl.LOCAL])
        authenticationUseCase.saveSession(result.session)
        urlUseCase.saveHomeWifiSsids(result.ssids.toSet())
        return true
    }

    private suspend fun updateDevice(): Boolean {
        val token = catch { FirebaseInstanceId.getInstance().instanceId.await() } ?: return false
        return catch { integrationUseCase.updateRegistration(
            appVersion= "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            manufacturer = Build.MANUFACTURER ?: "UNKNOWN",
            model = Build.MODEL ?: "UNKNOWN",
            osVersion = Build.VERSION.SDK_INT.toString(),
            pushToken = token.token
        ) } != null
    }

    override fun finish() {
        handler.removeCallbacks(delayedShow)
        mainScope.cancel()
        syncManager.cancel()
    }

}