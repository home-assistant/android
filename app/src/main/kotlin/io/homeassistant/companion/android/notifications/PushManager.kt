package io.homeassistant.companion.android.notifications

import io.homeassistant.companion.android.common.notifications.PushProvider
import javax.inject.Inject

class PushManager @Inject constructor(
    val providers: Map<String, @JvmSuppressWildcards PushProvider>
) {

    companion object {
        private const val FCM_SOURCE = FirebaseCloudMessagingProvider.SOURCE
    }

    val defaultProvider: PushProvider get() {
        return providers[FCM_SOURCE]!!
    }

    suspend fun getToken(): String {
        return providers[FCM_SOURCE]!!.getToken()
    }
}
