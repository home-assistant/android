package io.homeassistant.companion.android.wear.settings

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.wear.activity.ConfirmationActivity
import io.homeassistant.companion.android.common.util.ProgressTimeLatch
import io.homeassistant.companion.android.wear.BuildConfig
import io.homeassistant.companion.android.wear.R
import io.homeassistant.companion.android.wear.background.SettingsSyncCallback
import io.homeassistant.companion.android.wear.background.SettingsSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SettingsPresenterImpl @Inject constructor(
    private val view: SettingsView,
    private val syncManager: SettingsSyncManager
) : SettingsPresenter, SettingsSyncCallback {

    private val mainScope = CoroutineScope(Dispatchers.Main + Job())
    private val progressLatch = ProgressTimeLatch(defaultValue = false, refreshingToggle = view::displaySyncInProgress)

    private val handler = Handler(Looper.getMainLooper())
    private val delayedShow = Runnable {
        view.showConfirmed(ConfirmationActivity.FAILURE_ANIMATION, R.string.ha_phone_app_not_reachable_short)
    }

    override fun onViewReady() {
        syncManager.syncCallback = this
    }

    override fun syncSettings() {
        mainScope.launch {
            progressLatch.refreshing = true
            val connectedDevice = withContext(Dispatchers.IO) { syncManager.getNodeWithInstalledApp() }
            if (connectedDevice == null) {
                progressLatch.refreshing = false
                view.showConfirmed(ConfirmationActivity.FAILURE_ANIMATION, R.string.ha_phone_app_not_reachable)
                return@launch
            }
            val result = withContext(Dispatchers.IO) { syncManager.sendMessage(connectedDevice.id) }
            handler.postDelayed(delayedShow, 5000)
            if (BuildConfig.DEBUG) {
                Log.d("SettingsPresenter", "Send sync message result: $result")
            }
        }
    }

    override fun onConfigReceived() = handler.removeCallbacks(delayedShow)

    override fun onInactiveSession() {
        progressLatch.refreshing = false
        view.showConfirmed(ConfirmationActivity.FAILURE_ANIMATION, R.string.ha_session_inactive)
    }

    override fun onConfigSynced() {
        progressLatch.refreshing = false
        view.showConfirmed(ConfirmationActivity.SUCCESS_ANIMATION, R.string.ha_settings_synced)
    }

    override fun finish() {
        handler.removeCallbacks(delayedShow)
        mainScope.cancel()
        syncManager.cancel()
    }

}