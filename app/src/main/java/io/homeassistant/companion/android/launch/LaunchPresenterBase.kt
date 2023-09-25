package io.homeassistant.companion.android.launch

import io.homeassistant.companion.android.common.data.authentication.SessionState
import io.homeassistant.companion.android.common.data.servers.ServerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

abstract class LaunchPresenterBase(
    private val view: LaunchView,
    internal val serverManager: ServerManager
) : LaunchPresenter {

    companion object {
        const val TAG = "LaunchPresenter"
    }

    internal val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())
    internal val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO)

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
    internal abstract fun resyncRegistration()
}
