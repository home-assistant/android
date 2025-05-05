package io.homeassistant.companion.android.launch

import android.content.Context
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.util.tryRegisterCurrentDistributor
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.UnifiedPush
import timber.log.Timber

class LaunchPresenterImpl @Inject constructor(
    view: LaunchView,
    serverManager: ServerManager
) : LaunchPresenterBase(view, serverManager) {
    override fun resyncRegistration() {
        if (!serverManager.isRegistered()) return
        val hasDistributor = UnifiedPush.tryRegisterCurrentDistributor(view as Context)
        serverManager.defaultServers.forEach {
            ioScope.launch {
                try {
                    serverManager.integrationRepository(it.id).updateRegistration(
                        DeviceRegistration(
                            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            pushEncrypt = hasDistributor
                        )
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
