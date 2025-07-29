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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

@ActivityScoped
class LaunchPresenterImpl @Inject constructor(
    @ActivityContext context: Context,
    serverManager: ServerManager,
    networkStatusMonitor: NetworkStatusMonitor,
) : LaunchPresenterBase(context as LaunchView, serverManager, networkStatusMonitor) {
    override suspend fun resyncRegistration() = withContext(Dispatchers.IO) {
        if (!serverManager.isRegistered()) return@withContext

        serverManager.defaultServers.forEach {
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
