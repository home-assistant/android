package io.homeassistant.companion.android.launch

import android.content.Context
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.notifications.PushManager
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

@ActivityScoped
class LaunchPresenterImpl @Inject constructor(
    @ActivityContext context: Context,
    serverManager: ServerManager,
    pushManager: PushManager
) : LaunchPresenterBase(context as LaunchView, serverManager, pushManager) {
    override fun resyncRegistration() {
        if (!serverManager.isRegistered()) return
        serverManager.defaultServers.forEach {
            ioScope.launch {
                try {
                    serverManager.integrationRepository(it.id).updateRegistration(
                        DeviceRegistration(
                            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            deviceName = null,
                            pushToken = pushManager.getToken()
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
