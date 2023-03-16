package io.homeassistant.companion.android.launch

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.onboarding.getMessagingToken
import kotlinx.coroutines.launch
import javax.inject.Inject

@ActivityScoped
class LaunchPresenterImpl @Inject constructor(
    @ActivityContext context: Context,
    serverManager: ServerManager
) : LaunchPresenterBase(context as LaunchView, serverManager) {
    override fun resyncRegistration() {
        if (!serverManager.isRegistered()) return
        serverManager.defaultServers.forEach {
            ioScope.launch {
                try {
                    serverManager.integrationRepository(it.id).updateRegistration(
                        DeviceRegistration(
                            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            null,
                            getMessagingToken()
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
