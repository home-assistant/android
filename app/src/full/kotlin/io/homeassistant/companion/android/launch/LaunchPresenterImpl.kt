package io.homeassistant.companion.android.launch

import android.content.Context
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.onboarding.getMessagingToken
import io.homeassistant.companion.android.util.tryRegisterCurrentDistributor
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.UnifiedPush
import timber.log.Timber

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
                    // Don't get a new push token if using UnifiedPush.
                    val messagingToken = if (!UnifiedPush.tryRegisterCurrentDistributor(view as Context)) {
                        getMessagingToken()
                    } else {
                        null
                    }
                    serverManager.integrationRepository(it.id).updateRegistration(
                        DeviceRegistration(
                            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            deviceName = null,
                            pushToken = messagingToken,
                            // A blank url indicates to use the build-time push url.
                            pushUrl = messagingToken?.let { "" },
                            pushEncrypt = messagingToken == null && UnifiedPush.getAckDistributor(view as Context) != null
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
