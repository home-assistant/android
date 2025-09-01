package io.homeassistant.companion.android.launch

import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.authentication.SessionState
import io.homeassistant.companion.android.common.data.network.NetworkState
import io.homeassistant.companion.android.common.data.network.NetworkStatusMonitor
import io.homeassistant.companion.android.common.data.servers.ServerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

abstract class LaunchPresenterBase(
    private val view: LaunchView,
    internal val serverManager: ServerManager,
    private val networkStatusMonitor: NetworkStatusMonitor,
) : LaunchPresenter {

    /**
     * A dedicated [CoroutineScope] to perform background tasks that should not block the UI.
     * This is crucial for operations like server resynchronization, especially when dealing with
     * multiple servers where one might be unresponsive.
     *
     * By launching tasks in this scope, we ensure that the UI remains responsive and is displayed
     * promptly, even if a server connection times out.
     *
     * **Note:** Using a dedicated scope like this means that the underlying Activity might not be
     * destroyed immediately if these background tasks are still running. This is a trade-off
     * for improved UI responsiveness in scenarios with potential network delays.
     *
     * We use a [SupervisorJob] on purpose because it ensures that if one task fails, it won't
     * impact the others.
     *
     * See https://github.com/home-assistant/android/issues/5689#issuecomment-3241605990
     */
    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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
                    .takeWhile { state ->
                        // Until the network is ready we continue to observe network status changes
                        !handleNetworkState(state)
                    }.collect()
            } else {
                view.displayOnBoarding(false)
            }
        } catch (e: IllegalArgumentException) { // Server was just removed, nothing is added
            Timber.e(e, "Issue checking servers falling back to onboarding")
            view.displayOnBoarding(false)
        }
    }

    private suspend fun handleNetworkState(state: NetworkState): Boolean {
        Timber.i("Current network state $state")
        return when (state) {
            NetworkState.READY_LOCAL, NetworkState.READY_REMOTE -> {
                view.dismissDialog()
                ioScope.launch {
                    resyncRegistration()
                }
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
    }

    override suspend fun setSessionExpireMillis(value: Long) = withContext(Dispatchers.IO) {
        if (serverManager.isRegistered()) serverManager.integrationRepository().setSessionExpireMillis(value)
    }

    override fun hasMultipleServers(): Boolean = serverManager.defaultServers.size > 1

    // TODO: This should be replaced by a worker https://github.com/home-assistant/android/issues/5724
    internal abstract suspend fun resyncRegistration()
}
