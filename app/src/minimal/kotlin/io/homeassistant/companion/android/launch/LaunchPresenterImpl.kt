package io.homeassistant.companion.android.launch

import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.network.NetworkStatusMonitor
import io.homeassistant.companion.android.common.data.servers.ServerManager
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class LaunchPresenterImpl @Inject constructor(
    view: LaunchView,
    serverManager: ServerManager,
    networkStatusMonitor: NetworkStatusMonitor,
) : LaunchPresenterBase(view, serverManager, networkStatusMonitor) {
    override suspend fun resyncRegistration() = withContext(Dispatchers.IO) {
        if (!serverManager.isRegistered()) return@withContext

        serverManager.defaultServers.forEach {
            try {
                serverManager.integrationRepository(it.id).updateRegistration(
                    DeviceRegistration(
                        "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
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
