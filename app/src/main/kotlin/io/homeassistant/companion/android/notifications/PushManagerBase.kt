package io.homeassistant.companion.android.notifications

import io.homeassistant.companion.android.common.notifications.PushProvider
import io.homeassistant.companion.android.common.notifications.id

abstract class PushManagerBase(
    override val providers: Map<Class<*>, @JvmSuppressWildcards PushProvider>
) : PushManager {

    abstract val defaultProvider: PushProvider?

    override fun getProvider(clazz: Class<*>): PushProvider? = providers[clazz]

    override fun getProvider(id: String): PushProvider? =
        providers.values.find { it.id() == id }

    /**
     * Get the push url for the default push provider.
     */
    override suspend fun getToken(): String? {
        return defaultProvider?.getToken()
    }
}
