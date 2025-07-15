package io.homeassistant.companion.android.notifications

import android.content.Context
import io.homeassistant.companion.android.common.notifications.PushProvider
import io.homeassistant.companion.android.common.notifications.id

abstract class PushManagerBase(
    override val providers: Map<Class<*>, @JvmSuppressWildcards PushProvider>
) : PushManager {
    override fun getProvider(clazz: Class<*>): PushProvider? = providers[clazz]

    override fun getProvider(id: String): PushProvider? =
        providers.values.find { it.id() == id }

    override fun getProviderForServer(context: Context, serverId: Int): PushProvider? =
        providers.values.find { it.isEnabled(context, serverId) }

    override suspend fun getToken(): String? {
        return defaultProvider?.getToken()
    }
}
