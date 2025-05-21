package io.homeassistant.companion.android.notifications

import io.homeassistant.companion.android.common.notifications.PushProvider
import javax.inject.Inject

class PushManager @Inject constructor(
    val providers: Map<Class<*>, @JvmSuppressWildcards PushProvider>
) {

    val defaultProvider: PushProvider get() {
        return providers[FirebasePushProvider::class.java]!!
    }

    /**
     * Get the push url for the default push provider.
     */
    suspend fun getToken(): String {
        return defaultProvider.getToken()
    }
}
