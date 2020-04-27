package io.homeassistant.companion.android.wear.launch

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.MessageEvent
import io.homeassistant.companion.android.common.util.ProgressTimeLatch
import io.homeassistant.companion.android.domain.authentication.AuthenticationUseCase
import io.homeassistant.companion.android.domain.authentication.Session
import io.homeassistant.companion.android.domain.authentication.SessionState
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import io.homeassistant.companion.android.domain.url.UrlUseCase
import io.homeassistant.companion.android.wear.BuildConfig
import io.homeassistant.companion.android.wear.R
import io.homeassistant.companion.android.wear.background.Result
import io.homeassistant.companion.android.wear.background.SettingsSyncCallback
import io.homeassistant.companion.android.wear.background.SettingsSyncManager
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
    private val syncManager: SettingsSyncManager,
    private val authenticationUseCase: AuthenticationUseCase,
    private val integrationUseCase: IntegrationUseCase
) : LaunchPresenter, SettingsSyncCallback {

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())
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
            val capabilityResult = withContext(Dispatchers.IO) { syncManager.getNodeWithInstalledApp() }
            if (capabilityResult == null || capabilityResult.result == Result.FAILURE) {
                progressLatch.refreshing = false
                view.displayUnreachable()
            } else if (capabilityResult.result == Result.NOT_NEARBY) {
                progressLatch.refreshing = false
                view.displayNotNearby()
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

    override fun onConfigReceived() = handler.removeCallbacks(delayedShow)

    override fun onInactiveSession() {
        progressLatch.refreshing = false
        view.displayInactiveSession()
    }

    override fun onConfigSynced() {
        progressLatch.refreshing = false
        view.displayNextScreen()
    }

    override fun onFinish() {
        syncManager.cancel()
        handler.removeCallbacks(delayedShow)
        mainScope.cancel()
    }
    
}