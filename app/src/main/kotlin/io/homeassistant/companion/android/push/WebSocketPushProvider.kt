package io.homeassistant.companion.android.push

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
 * This is always available and uses a persistent connection.
 * Used by the minimal flavor when no other provider is selected.
 */
@Singleton
class WebSocketPushProvider @Inject constructor(
    private val serverManager: ServerManager,
    private val settingsDao: SettingsDao,
) : PushProvider {

    override val name: String = NAME

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
