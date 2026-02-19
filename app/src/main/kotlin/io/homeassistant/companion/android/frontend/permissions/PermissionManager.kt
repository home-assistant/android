package io.homeassistant.companion.android.frontend.permissions

import android.webkit.PermissionRequest
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.settings.SensorUpdateFrequencySetting
import io.homeassistant.companion.android.database.settings.Setting
import io.homeassistant.companion.android.database.settings.SettingsDao
import io.homeassistant.companion.android.database.settings.WebsocketSetting
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * Represents a pending WebView permission request that requires Android runtime permissions.
 *
 * @param webViewRequest The original [PermissionRequest] from the WebView to grant/deny after the user responds
 * @param androidPermissions The Android runtime permissions that need to be requested
 * @param webViewResourcesByPermission Maps each Android permission to its corresponding WebView resource string,
 *        so that after granting we know which WebView resources to approve
 */
internal data class PendingWebViewPermissionRequest(
    val webViewRequest: PermissionRequest,
    val androidPermissions: List<String>,
    val webViewResourcesByPermission: Map<String, String>,
)

/**
 * Manages permission prompts for the frontend screen.
 *
 * Determines which permissions should be requested from the user and handles the results of permission grants/denials.
 */
internal class PermissionManager @Inject constructor(
    private val serverManager: ServerManager,
    private val settingsDao: SettingsDao,
    @HasFcmPushSupport private val hasFcmPushSupport: Boolean,
    private val notificationStatusProvider: NotificationStatusProvider,
    private val permissionChecker: PermissionChecker,
) {

    private val _pendingWebViewPermission = MutableStateFlow<PendingWebViewPermissionRequest?>(null)

    /** The current pending WebView permission request that needs user approval, or null if none. */
    val pendingWebViewPermission: StateFlow<PendingWebViewPermissionRequest?> = _pendingWebViewPermission.asStateFlow()

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

    /**
     * Processes a WebView permission request.
     *
     * Auto-grants resources for which the app already holds Android runtime permissions.
     * For remaining resources, stores the request as [pendingWebViewPermission] so the UI
     * can launch the system permission dialog.
     *
     * @param request The [PermissionRequest] from the WebView's `onPermissionRequest` callback
     */
    fun onWebViewPermissionRequest(request: PermissionRequest?) {
        if (request == null) return

        val alreadyGranted = mutableListOf<String>()
        val toBeGranted = mutableListOf<String>()
        val resourcesByPermission = mutableMapOf<String, String>()

        for (resource in request.resources) {
            val androidPermission = mapToAndroidPermission(resource) ?: continue

            if (permissionChecker.hasPermission(androidPermission)) {
                alreadyGranted.add(resource)
            } else {
                toBeGranted.add(androidPermission)
                resourcesByPermission[androidPermission] = resource
            }
        }

        if (alreadyGranted.isNotEmpty()) {
            Timber.d("Auto-granting WebView resources: $alreadyGranted")
            request.grant(alreadyGranted.toTypedArray())
        }

        if (toBeGranted.isNotEmpty()) {
            Timber.d("Requesting Android permissions for WebView: $toBeGranted")
            _pendingWebViewPermission.value = PendingWebViewPermissionRequest(
                webViewRequest = request,
                androidPermissions = toBeGranted,
                webViewResourcesByPermission = resourcesByPermission,
            )
        }
    }

    /**
     * Resolves the pending WebView permission request after the user responds to the system
     * permission dialog.
     *
     * Grants WebView resources for permissions the user approved. Denies the request if nothing
     * was granted.
     *
     * @param results Map of Android permission to whether it was granted
     */
    fun onWebViewPermissionResult(results: Map<String, Boolean>) {
        val pending = _pendingWebViewPermission.value ?: return
        _pendingWebViewPermission.value = null

        val grantedResources = results
            .filter { (_, granted) -> granted }
            .mapNotNull { (permission, _) -> pending.webViewResourcesByPermission[permission] }

        if (grantedResources.isNotEmpty()) {
            Timber.d("Granting WebView resources after user approval: $grantedResources")
            pending.webViewRequest.grant(grantedResources.toTypedArray())
        } else {
            Timber.d("User denied all requested permissions, denying WebView request")
            pending.webViewRequest.deny()
        }
    }

    private fun mapToAndroidPermission(webViewResource: String): String? {
        return when (webViewResource) {
            PermissionRequest.RESOURCE_VIDEO_CAPTURE -> android.Manifest.permission.CAMERA
            PermissionRequest.RESOURCE_AUDIO_CAPTURE -> android.Manifest.permission.RECORD_AUDIO
            else -> {
                Timber.w("Unknown WebView permission resource: $webViewResource")
                null
            }
        }
    }
}
