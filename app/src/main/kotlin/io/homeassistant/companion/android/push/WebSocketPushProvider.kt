package io.homeassistant.companion.android.push

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.push.PushProvider
import io.homeassistant.companion.android.common.push.PushRegistrationResult
import io.homeassistant.companion.android.database.settings.SettingsDao
import io.homeassistant.companion.android.database.settings.WebsocketSetting
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Push provider implementation backed by a persistent WebSocket connection.
 *
 * This is always available as a fallback, but has the lowest priority because
 * it requires a persistent connection and consumes more battery.
 * Used by the minimal flavor when no UnifiedPush distributor is available.
 */
@Singleton
class WebSocketPushProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serverManager: ServerManager,
    private val settingsDao: SettingsDao,
) : PushProvider {

    override val name: String = NAME

    override val priority: Int = 30

    override val requiresPersistentConnection: Boolean = true

    override suspend fun isAvailable(): Boolean = true

    override suspend fun isActive(): Boolean {
        if (!serverManager.isRegistered()) return false
        return serverManager.servers().any { server ->
            val setting = settingsDao.get(server.id)?.websocketSetting
            setting != null && setting != WebsocketSetting.NEVER
        }
    }

    override suspend fun register(): PushRegistrationResult {
        Timber.d("WebSocket push provider registered (persistent connection mode)")
        return PushRegistrationResult(
            pushToken = "",
            pushUrl = null,
            encrypt = false,
        )
    }

    override suspend fun unregister() {
        Timber.d("WebSocket push provider unregistered")
    }

    companion object {
        const val NAME = "WebSocket"
    }
}
