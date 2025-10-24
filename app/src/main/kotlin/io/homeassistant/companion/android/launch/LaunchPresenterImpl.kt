package io.homeassistant.companion.android.launch

import android.content.Context
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.authentication.SessionState
import io.homeassistant.companion.android.common.data.network.NetworkState
import io.homeassistant.companion.android.common.data.network.NetworkStatusMonitor
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.ResyncRegistrationWorker.Companion.enqueueResyncRegistration
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withContext
import timber.log.Timber

@ActivityScoped
class LaunchPresenterImpl @Inject constructor(
    private val workManager: WorkManager,
    @ActivityContext context: Context,
    internal val serverManager: ServerManager,
    private val networkStatusMonitor: NetworkStatusMonitor,
) : LaunchPresenter {
    private val view: LaunchView = context as LaunchView

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
                workManager.enqueueResyncRegistration()
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
}
