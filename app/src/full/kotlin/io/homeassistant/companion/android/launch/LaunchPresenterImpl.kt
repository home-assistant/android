package io.homeassistant.companion.android.launch

import android.content.Context
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.network.NetworkStatusMonitor
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.onboarding.getMessagingToken
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

@ActivityScoped
class LaunchPresenterImpl @Inject constructor(
    @ActivityContext context: Context,
    serverManager: ServerManager,
    networkStatusMonitor: NetworkStatusMonitor,
) : LaunchPresenterBase(context as LaunchView, serverManager, networkStatusMonitor) {

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
     * for improved UI responsiveness in scenarios with potential network delays. If we
     * introduce a new server state like disable we could maybe bring this back in the flow.
     *
     * We use a [SupervisorJob] on purpose because it ensures that if one task fails, it won't
     * impact the others.
     *
     * See https://github.com/home-assistant/android/issues/5689#issuecomment-3241605990
     */
    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override suspend fun resyncRegistration() {
        if (!serverManager.isRegistered()) return

        serverManager.defaultServers.forEach {
            ioScope.launch {
                try {
                    serverManager.integrationRepository(it.id).updateRegistration(
                        DeviceRegistration(
                            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            null,
                            getMessagingToken(),
                        ),
                    )
                    serverManager.integrationRepository(it.id).getConfig() // Update cached data
                    serverManager.webSocketRepository(it.id).getCurrentUser() // Update cached data
                } catch (e: Exception) {
                    Timber.e(e, "Issue updating Registration")
                }
            }
        }
    }
}
