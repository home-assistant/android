package io.homeassistant.companion.android.launch

import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.data.authentication.SessionState
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.notifications.PushManager
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

class LaunchPresenterImpl @Inject constructor(
    private val view: LaunchView,
    private val serverManager: ServerManager,
    private val pushManager: PushManager
) : LaunchPresenter {

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    override fun onViewReady() {
        mainScope.launch {
            // Remove any invalid servers (incomplete, partly migrated from another device)
            serverManager.defaultServers
                .filter { serverManager.authenticationRepository(it.id).getSessionState() == SessionState.ANONYMOUS }
                .forEach { serverManager.removeServer(it.id) }

            try {
                if (
                    serverManager.isRegistered() &&
                    serverManager.authenticationRepository().getSessionState() == SessionState.CONNECTED
                ) {
                    resyncRegistration()
                    view.displayWebview()
                } else {
                    view.displayOnBoarding(false)
                }
            } catch (e: IllegalArgumentException) { // Server was just removed, nothing is added
                view.displayOnBoarding(false)
            }
        }
    }

    override fun setSessionExpireMillis(value: Long) {
        mainScope.launch {
            if (serverManager.isRegistered()) serverManager.integrationRepository().setSessionExpireMillis(value)
        }
    }

    override fun hasMultipleServers(): Boolean = serverManager.defaultServers.size > 1

    override fun onFinish() {
        mainScope.cancel()
    }

    // TODO: This should probably go in settings?
    private fun resyncRegistration() {
        if (!serverManager.isRegistered()) return
        serverManager.defaultServers.forEach {
            ioScope.launch {
                try {
                    serverManager.integrationRepository(it.id).updateRegistration(
                        DeviceRegistration(
                            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            pushToken = pushManager.getToken()
                        )
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Issue updating Registration")
                }
            }
        }
    }
}
