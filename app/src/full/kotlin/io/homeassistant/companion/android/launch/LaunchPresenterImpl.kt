package io.homeassistant.companion.android.launch

import android.content.Context
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.onboarding.getMessagingToken
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

@ActivityScoped
class LaunchPresenterImpl @Inject constructor(
    @ActivityContext context: Context,
    serverManager: ServerManager,
) : LaunchPresenterBase(context as LaunchView, serverManager) {
    override fun resyncRegistration() {
        ioScope.launch {
            if (!serverManager.isRegistered()) return@launch
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
}
