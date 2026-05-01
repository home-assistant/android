package io.homeassistant.companion.android.frontend.permissions

import android.annotation.SuppressLint
import android.os.Build
import android.webkit.PermissionRequest as WebViewPermissionRequest
import androidx.annotation.VisibleForTesting
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.NotificationStatusProvider
import io.homeassistant.companion.android.common.util.PermissionChecker
import io.homeassistant.companion.android.common.util.SingleSlotQueue
import io.homeassistant.companion.android.database.settings.SensorUpdateFrequencySetting
import io.homeassistant.companion.android.database.settings.Setting
import io.homeassistant.companion.android.database.settings.SettingsDao
import io.homeassistant.companion.android.database.settings.WebsocketSetting
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

/**
 * Snapshot of the WebView's requested resources: which can be auto-granted now, which still
 * need user approval, and how to map back from each Android permission to the WebView resource
 * string it originated from.
 */
private data class WebViewPermissionStatus(
    val alreadyGranted: List<String>,
    val toBeGranted: List<String>,
    val resourcesByPermission: Map<String, String>,
)

/**
 * Centralises all Android runtime permission request/result for the frontend screen.
 *
 * Each `check*` / `on*` method runs the full request/response cycle: build a [PermissionRequest],
 * suspend until the user responds via the request's callback, then react to the result inline.
 * The slot is freed automatically including on coroutine cancellation by the underlying
 * [SingleSlotQueue.awaitResult].
 *
 * Concurrent triggers are waiting in FIFO order: a second method call suspends until the
 * current request is resolved.
 */
internal class PermissionManager @VisibleForTesting constructor(
    private val serverManager: ServerManager,
    private val settingsDao: SettingsDao,
    @FcmSupport private val fcmSupport: Boolean,
    private val notificationStatusProvider: NotificationStatusProvider,
    private val permissionChecker: PermissionChecker,
    // Need for testing to avoid the need of Robolectric
    private val sdkInt: Int,
) {

    @Inject
    constructor(
        serverManager: ServerManager,
        settingsDao: SettingsDao,
        @FcmSupport fcmSupport: Boolean,
        notificationStatusProvider: NotificationStatusProvider,
        permissionChecker: PermissionChecker,
    ) : this(
        serverManager = serverManager,
        settingsDao = settingsDao,
        fcmSupport = fcmSupport,
        notificationStatusProvider = notificationStatusProvider,
        permissionChecker = permissionChecker,
        sdkInt = Build.VERSION.SDK_INT,
    )

    private val queue = SingleSlotQueue<PermissionRequest>()

    /** The current pending permission request that needs user approval, or null if none. */
    val pendingPermissionRequest: StateFlow<PermissionRequest?> = queue

    /**
     * Checks whether the notification permission request should be made and, if so,
     * enqueues a [PermissionRequest.Notification].
     *
     * Does nothing on pre-TIRAMISU devices (POST_NOTIFICATIONS does not exist).
     *
     * Whether to ask depends on the flavor:
     * - **Full** (FCM available): skips if notifications are already enabled system-wide, since
     *   FCM handles push delivery without websocket configuration.
     * - **Minimal** (no FCM): always respects the per-server stored preference, allowing the user
     *   to configure websocket-based notification fallback.
     *
     * If the user dismisses the request without explicitly answering, no preference is persisted.
     *
     * @param serverId The server to check notification preferences for
     */
    @SuppressLint("NewApi")
    suspend fun checkNotificationPermission(serverId: Int) {
        if (sdkInt < Build.VERSION_CODES.TIRAMISU) return
        if (!shouldAskNotificationPermission(serverId)) return

        val granted: Boolean? = queue.awaitResult { resolve ->
            PermissionRequest.Notification(
                serverId = serverId,
                onResult = { granted -> resolve(granted) },
                onDismiss = { resolve(null) },
            )
        }
        if (granted != null) {
            onNotificationPermissionResult(serverId = serverId, granted = granted)
        }
    }

    /**
     * Ensures the app has permission to write to external storage for a download.
     *
     * Returns `true` immediately on API 29+ (scoped storage no permission needed) and when the
     * permission is already granted. Otherwise enqueues a [PermissionRequest.ExternalStorage],
     * suspends until the user responds, and returns whether they granted it. The caller can then
     * decide whether to proceed with the download.
     *
     * @return `true` if the download can proceed (permission available or not needed); `false` if
     *         the user declined the permission
     */
    suspend fun checkStoragePermissionForDownload(): Boolean {
        if (sdkInt >= Build.VERSION_CODES.Q) return true
        if (permissionChecker.hasPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)) return true

        Timber.d("Storage permission required for download, awaiting user response")
        return queue.awaitResult { onResult ->
            PermissionRequest.ExternalStorage(onResult = onResult)
        }
    }

    /**
     * Processes a WebView permission request (camera, microphone).
     *
     * Resources whose Android permissions are already granted are deferred so they can be granted
     * together with any newly-approved resources in a single [WebViewPermissionRequest.grant] call.
     *
     * If any permissions still need to be requested, suspends until the user responds and then
     * grants/denies the original WebView request accordingly. Otherwise, auto-grants the
     * already-granted resources without showing UI.
     */
    suspend fun onWebViewPermissionRequest(request: WebViewPermissionRequest?) {
        if (request == null) return

        val status = assessWebViewPermissions(request)
        if (status.toBeGranted.isEmpty()) {
            autoGrantWebViewResources(request, status.alreadyGranted)
            return
        }
        val grantedPermissions = queue.awaitResult { onResult ->
            PermissionRequest.WebView(androidPermissions = status.toBeGranted, onResult = onResult)
        }
        resolveWebViewRequest(request, status, grantedPermissions)
    }

    /**
     * Splits the WebView request's resources into those whose Android permissions are already
     * granted (and can be auto-granted in one batch) and those that still need to be requested.
     */
    private fun assessWebViewPermissions(request: WebViewPermissionRequest): WebViewPermissionStatus {
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
        return WebViewPermissionStatus(
            alreadyGranted = alreadyGranted,
            toBeGranted = toBeGranted,
            resourcesByPermission = resourcesByPermission,
        )
    }

    /**
     * Grants [alreadyGranted] resources without showing UI, or returns silently when there is
     * nothing to grant.
     */
    private fun autoGrantWebViewResources(request: WebViewPermissionRequest, alreadyGranted: List<String>) {
        if (alreadyGranted.isEmpty()) return
        Timber.d("Auto-granting WebView resources: $alreadyGranted")
        request.grant(alreadyGranted.toTypedArray())
    }

    /**
     * Combines previously-granted resources from [status] with the resources the user has just
     * approved and resolves the original [request] in a single grant/deny call.
     */
    private fun resolveWebViewRequest(
        request: WebViewPermissionRequest,
        status: WebViewPermissionStatus,
        grantedPermissions: Map<String, Boolean>,
    ) {
        val newlyGrantedResources = grantedPermissions
            .filter { (_, granted) -> granted }
            .mapNotNull { (permission, _) -> status.resourcesByPermission[permission] }
        val allGrantedResources = status.alreadyGranted + newlyGrantedResources

        if (allGrantedResources.isNotEmpty()) {
            Timber.d("Granting WebView resources: $allGrantedResources")
            request.grant(allGrantedResources.toTypedArray())
        } else {
            Timber.d("User denied all requested permissions, denying WebView request")
            request.deny()
        }
    }

    /**
     * Persists the notification permission result.
     *
     * When granted on the minimal flavor (no FCM), enables websocket-based notifications.
     * Always marks the prompt as answered so it is not shown again for this server.
     */
    private suspend fun onNotificationPermissionResult(serverId: Int, granted: Boolean) {
        if (granted && !fcmSupport) {
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

    private suspend fun shouldAskNotificationPermission(serverId: Int): Boolean {
        val isPermissionAlreadyGranted = notificationStatusProvider.areNotificationsEnabled()
        val shouldAskNotificationPermission = serverManager.integrationRepository(serverId)
            .shouldAskNotificationPermission()

        if (isPermissionAlreadyGranted && fcmSupport) {
            serverManager.integrationRepository(serverId).setAskNotificationPermission(false)
            return false
        }

        return shouldAskNotificationPermission ?: true
    }

    private fun mapToAndroidPermission(webViewResource: String): String? {
        return when (webViewResource) {
            WebViewPermissionRequest.RESOURCE_VIDEO_CAPTURE -> android.Manifest.permission.CAMERA
            WebViewPermissionRequest.RESOURCE_AUDIO_CAPTURE -> android.Manifest.permission.RECORD_AUDIO
            else -> {
                Timber.w("Unknown WebView permission resource: $webViewResource")
                null
            }
        }
    }
}
