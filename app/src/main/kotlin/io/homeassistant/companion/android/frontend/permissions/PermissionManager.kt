package io.homeassistant.companion.android.frontend.permissions

import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.settings.SensorUpdateFrequencySetting
import io.homeassistant.companion.android.database.settings.Setting
import io.homeassistant.companion.android.database.settings.SettingsDao
import io.homeassistant.companion.android.database.settings.WebsocketSetting
import javax.inject.Inject

/**
 * Manages permission prompts.
 *
 * Determines which permissions should be requested from the user and handles
 * the results of permission grants/denials.
 */
internal class PermissionManager @Inject constructor(
    private val serverManager: ServerManager,
    private val settingsDao: SettingsDao,
    @HasFcmPushSupport private val hasFcmPushSupport: Boolean,
    private val notificationStatusProvider: NotificationStatusProvider,
) {

    /**
     * Determines whether the notification permission prompt should be shown for the given server.
     *
     * The behavior differs between flavors:
     * - **Full flavor** (FCM available): If notification permission is already granted system-wide,
     *   returns `false` and persists this decision. FCM handles push notifications so there is no
     *   need to configure websocket settings.
     * - **Minimal flavor** (no FCM): Always respects the per-server stored preference regardless
     *   of the system permission state. This allows the prompt to configure websocket-based
     *   notification fallback even if the system permission was granted externally.
     *
     * @return `true` if the notification permission prompt should be shown
     */
    suspend fun shouldAskNotificationPermission(serverId: Int): Boolean {
        val isPermissionAlreadyGranted = notificationStatusProvider.areNotificationsEnabled()
        val shouldAskNotificationPermission = serverManager.integrationRepository(serverId)
            .shouldAskNotificationPermission()

        if (isPermissionAlreadyGranted && hasFcmPushSupport) {
            serverManager.integrationRepository(serverId).setAskNotificationPermission(false)
            return false
        }

        return shouldAskNotificationPermission ?: true
    }

    /**
     * Handles the result of the notification permission request.
     *
     * When granted on the minimal flavor (no FCM), enables websocket-based notifications by
     * inserting settings with [WebsocketSetting.ALWAYS] and [SensorUpdateFrequencySetting.NORMAL].
     * Always persists the decision to not prompt again for the given server.
     *
     * @param serverId The server ID to store the preference for
     * @param granted Whether the user granted the notification permission
     */
    suspend fun onNotificationPermissionResult(serverId: Int, granted: Boolean) {
        if (granted && !hasFcmPushSupport) {
            settingsDao.insert(
                Setting(
                    id = serverId,
                    websocketSetting = WebsocketSetting.ALWAYS,
                    sensorUpdateFrequency = SensorUpdateFrequencySetting.NORMAL,
                ),
            )
        }
        serverManager.integrationRepository(serverId).setAskNotificationPermission(false)
    }
}
