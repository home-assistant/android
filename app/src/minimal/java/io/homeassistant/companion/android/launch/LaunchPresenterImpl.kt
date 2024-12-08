package io.shpro.companion.android.launch

import android.util.Log
import io.shpro.companion.android.BuildConfig
import io.shpro.companion.android.common.data.integration.DeviceRegistration
import io.shpro.companion.android.common.data.servers.ServerManager
import javax.inject.Inject
import kotlinx.coroutines.launch

class LaunchPresenterImpl @Inject constructor(
    view: LaunchView,
    serverManager: ServerManager
) : LaunchPresenterBase(view, serverManager) {
    override fun resyncRegistration() {
        if (!serverManager.isRegistered()) return
        serverManager.defaultServers.forEach {
            ioScope.launch {
                try {
                    serverManager.integrationRepository(it.id).updateRegistration(
                        DeviceRegistration(
                            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
                        )
                    )
                    serverManager.integrationRepository(it.id).getConfig() // Update cached data
                    serverManager.webSocketRepository(it.id).getCurrentUser() // Update cached data
                } catch (e: Exception) {
                    Log.e(TAG, "Issue updating Registration", e)
                }
            }
        }
    }
}
