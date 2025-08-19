package io.homeassistant.companion.android.launch

import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.authentication.SessionState
import io.homeassistant.companion.android.common.data.network.NetworkState
import io.homeassistant.companion.android.common.data.network.NetworkStatusMonitor
import io.homeassistant.companion.android.common.data.servers.ServerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

abstract class LaunchPresenterBase(
    private val view: LaunchView,
    internal val serverManager: ServerManager,
    private val networkStatusMonitor: NetworkStatusMonitor,
) : LaunchPresenter {

    override suspend fun onViewReady(serverUrlToOnboard: String?) {
        // Remove any invalid servers (incomplete, partly migrated from another device)
        serverManager.defaultServers
            .filter { serverManager.authenticationRepository(it.id).getSessionState() == SessionState.ANONYMOUS }
            .forEach { serverManager.removeServer(it.id) }

        try {
            if (serverUrlToOnboard != null) {
                view.displayOnBoarding(false, serverUrlToOnboard)
                return
            }

            val activeServer = serverManager.getServer(ServerManager.SERVER_ID_ACTIVE)
            if (
                serverManager.isRegistered() &&
                serverManager.authenticationRepository().getSessionState() == SessionState.CONNECTED &&
                activeServer != null
            ) {
                networkStatusMonitor.observeNetworkStatus(activeServer.connection)
                    .collectLatest { state ->
                        if (handleNetworkState(state)) return@collectLatest
                    }
            } else {
                view.displayOnBoarding(false)
            }
        } catch (e: IllegalArgumentException) { // Server was just removed, nothing is added
            view.displayOnBoarding(false)
        }
    }

    private suspend fun handleNetworkState(state: NetworkState): Boolean = when (state) {
        NetworkState.READY_LOCAL, NetworkState.READY_REMOTE -> {
            view.dismissDialog()
            resyncRegistration()
            view.displayWebView()
            true
        }

        // the activity has a CircularProgressIndicator running
        NetworkState.CONNECTING -> {
            view.dismissDialog()
            false
        }

        NetworkState.UNAVAILABLE -> {
            view.displayAlertMessageDialog(R.string.error_connection_failed_no_network)
            false
        }
    }

    override suspend fun setSessionExpireMillis(value: Long) = withContext(Dispatchers.IO) {
        if (serverManager.isRegistered()) serverManager.integrationRepository().setSessionExpireMillis(value)
    }

    override fun hasMultipleServers(): Boolean = serverManager.defaultServers.size > 1

    // TODO: This should probably go in settings?
    internal abstract suspend fun resyncRegistration()
}
