package io.homeassistant.companion.android.notifications

import android.content.Context
import io.homeassistant.companion.android.common.notifications.PushProvider
import io.homeassistant.companion.android.database.settings.PushProviderSetting
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

class FirebaseCloudMessagingProvider @Inject constructor() : PushProvider {

    // Firebase Cloud Messaging depends on Google Play Services,
    // and as a result FCM is not supported with the minimal flavor

    companion object {
        const val SOURCE = "FCM"
    }

    override val setting = PushProviderSetting.FCM

    override fun isAvailable(context: Context): Boolean = false

    override fun isEnabled(context: Context): Boolean = false

    override fun isEnabled(context: Context, serverId: Int): Boolean = false

    override fun getEnabledServers(context: Context): Set<Int> = emptySet()

    override suspend fun getToken(): String = ""

    override fun onMessage(context: Context, notificationData: Map<String, String>) {}

    override suspend fun updateRegistration(context: Context, coroutineScope: CoroutineScope) {}
}
