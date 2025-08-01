package io.homeassistant.companion.android.launch

import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.servers.ServerManager
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

class LaunchPresenterImpl @Inject constructor(view: LaunchView, serverManager: ServerManager) :
    LaunchPresenterBase(view, serverManager) {
    override fun resyncRegistration() {
        ioScope.launch {
            if (!serverManager.isRegistered()) return@launch
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
}
