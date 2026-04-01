package io.homeassistant.companion.android.frontend.permissions

import android.annotation.SuppressLint
import android.os.Build
import android.webkit.PermissionRequest
import androidx.annotation.VisibleForTesting
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.common.util.NotificationStatusProvider
import io.homeassistant.companion.android.common.util.PermissionChecker
import io.homeassistant.companion.android.database.settings.SensorUpdateFrequencySetting
import io.homeassistant.companion.android.database.settings.Setting
import io.homeassistant.companion.android.database.settings.SettingsDao
import io.homeassistant.companion.android.database.settings.WebsocketSetting
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * Centralizes all Android runtime permission request/result for the frontend screen.
 *
 * ## How it works
 *
 * All permission requests flow through a single [pendingPermissionRequest] state.
 * The UI observes this flow and either launches the system permission dialog or shows a custom
 * prompt (notification bottom sheet), depending on the [PendingPermissionRequest] type.
 *
 * Requests are queued: if a request is already in-flight, new requests suspend until the
 * current one is resolved via [onPermissionResult] or dismissed via [dismissPendingPermission].
 *
 * ## Typical flow
 *
 * 1. A trigger (WebView callback, frontend connect, download) calls one of the `check*` / `on*` methods
 * 2. The method creates a [PendingPermissionRequest] and emits it (suspending if one is already active)
 * 3. The composable layer observes the change and shows the appropriate UI
 * 4. The user responds — the composable calls [onPermissionResult] or [dismissPendingPermission]
 * 5. This class dispatches the result (grant/deny the WebView request, persist notification choice, etc.)
 * 6. The pending slot is cleared, unblocking the next queued request if any
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

    /**
     * Single-slot holder that queues permission requests.
     *
     * [emit] suspends until the slot is free (value is `null`), ensuring only one permission
     * dialog is active at a time. [clear] frees the slot and unblocks the next waiting caller.
     */
    @OptIn(ExperimentalForInheritanceCoroutinesApi::class)
    private class PendingPermissionManager(
        private val _state: MutableStateFlow<PendingPermissionRequest?> = MutableStateFlow(null),
    ) : StateFlow<PendingPermissionRequest?> by _state.asStateFlow() {
        suspend fun emit(request: PendingPermissionRequest) {
            _state.first { it == null }
            _state.value = request
        }

        fun clear() {
            _state.value = null
        }
    }

    private val _pendingPermissionRequest = PendingPermissionManager()

    /** The current pending permission request that needs user approval, or null if none. */
    val pendingPermissionRequest: StateFlow<PendingPermissionRequest?> = _pendingPermissionRequest

    /**
     * Checks whether the notification permission prompt should be shown and, if so,
     * enqueues a [PendingPermissionRequest.Notification].
     *
     * Does nothing on pre-TIRAMISU devices (POST_NOTIFICATIONS does not exist).
     *
     * Suspends if another permission request is already in-flight, and resumes once
     * the slot is free.
     *
     * Whether to prompt depends on the flavor:
     * - **Full** (FCM available): skips if notification permission is already granted system-wide,
     *   since FCM handles push delivery without websocket configuration.
     * - **Minimal** (no FCM): always respects the per-server stored preference, allowing the user
     *   to configure websocket-based notification fallback.
     *
     * @param serverId The server to check notification preferences for
     */
    @SuppressLint("NewApi")
    suspend fun checkNotificationPermission(serverId: Int) {
        if (sdkInt < Build.VERSION_CODES.TIRAMISU) return
        if (!shouldAskNotificationPermission(serverId)) return

        _pendingPermissionRequest.emit(PendingPermissionRequest.Notification(serverId = serverId))
    }

    /**
     * Processes a WebView permission request (camera, microphone).
     *
     * Resources for which the app already holds Android runtime permissions are collected
     * and deferred so they can be granted together with any newly-approved resources in a
     * single [PermissionRequest.grant] call.
     *
     * If permissions still need to be requested, a [PendingPermissionRequest.WebView] is enqueued.
     * Suspends if another permission request is already in-flight.
     *
     * @param request The [PermissionRequest] from the WebView's `onPermissionRequest` callback
     */
    suspend fun onWebViewPermissionRequest(request: PermissionRequest?) {
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

        if (toBeGranted.isNotEmpty()) {
            Timber.d("Requesting Android permissions for WebView: $toBeGranted (already granted: $alreadyGranted)")
            _pendingPermissionRequest.emit(
                PendingPermissionRequest.WebView(
                    webViewRequest = request,
                    androidPermissions = toBeGranted,
                    webViewResourcesByPermission = resourcesByPermission,
                    alreadyGrantedResources = alreadyGranted,
                ),
            )
        } else if (alreadyGranted.isNotEmpty()) {
            Timber.d("Auto-granting WebView resources: $alreadyGranted")
            request.grant(alreadyGranted.toTypedArray())
        }
    }

    /**
     * Checks whether storage permission is needed for a download and, if so, enqueues a
     * [PendingPermissionRequest.StorageForDownload].
     *
     * On API 29+ (scoped storage) or when the permission is already granted, returns `false`
     * immediately without enqueuing anything.
     *
     * Suspends if another permission request is already in-flight.
     *
     * @param onGranted Callback invoked after the user grants the permission, typically
     *        used to retry the download that was blocked
     * @return `true` if the download must wait for permission; `false` if it can proceed now
     */
    suspend fun requiresStoragePermissionForDownload(onGranted: () -> Unit): Boolean {
        if (sdkInt >= Build.VERSION_CODES.Q) return false
        if (permissionChecker.hasPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)) return false

        Timber.d("Storage permission required for download, deferring until granted")
        _pendingPermissionRequest.emit(PendingPermissionRequest.StorageForDownload(onGranted = onGranted))
        return true
    }

    /**
     * Dispatches the result of a permission dialog to the appropriate handler.
     *
     * Must only be called when a request is in-flight; triggers [FailFast] otherwise.
     * Clears the pending slot after dispatching, which unblocks the next queued request.
     *
     * @param results Map of Android permission to whether it was granted
     */
    suspend fun onPermissionResult(results: Map<String, Boolean>) {
        val pending = _pendingPermissionRequest.value

        if (pending == null) {
            FailFast.fail { "Should have in-flight permission while calling onPermissionResult" }
            return
        }

        _pendingPermissionRequest.clear()

        when (pending) {
            is PendingPermissionRequest.WebView -> resolveWebViewPermission(pending, results)
            is PendingPermissionRequest.StorageForDownload -> {
                if (results.values.any { it }) {
                    pending.onGranted()
                }
            }

            is PendingPermissionRequest.Notification -> {
                val granted = results.values.any { it }
                onNotificationPermissionResult(serverId = pending.serverId, granted = granted)
            }
        }
    }

    /**
     * Dismisses the current pending permission request without invoking any callbacks.
     *
     * Used when the user dismisses a prompt without making an explicit choice (e.g. swiping
     * away the notification bottom sheet). The request may reappear on the next trigger.
     *
     * Must only be called when a request is in-flight; triggers [FailFast] otherwise.
     */
    fun dismissPendingPermission() {
        FailFast.failWhen(_pendingPermissionRequest.value == null) {
            "Should have in-flight permission while calling dismissPendingPermission"
        }
        _pendingPermissionRequest.clear()
    }

    /**
     * Persists the notification permission result.
     *
     * When granted on the minimal flavor (no FCM), enables websocket-based notifications.
     * Always marks the prompt as answered so it is not shown again for this server.
     */
    @VisibleForTesting
    suspend fun onNotificationPermissionResult(serverId: Int, granted: Boolean) {
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

    /**
     * Resolves a WebView permission request by granting approved resources and denying the rest.
     *
     * Combines resources that were already granted at request time with resources for which the
     * user just approved the Android permission, then issues a single [PermissionRequest.grant]
     * call. If nothing was granted at all, denies the entire request.
     */
    private fun resolveWebViewPermission(pending: PendingPermissionRequest.WebView, results: Map<String, Boolean>) {
        val newlyGrantedResources = results
            .filter { (_, granted) -> granted }
            .mapNotNull { (permission, _) -> pending.webViewResourcesByPermission[permission] }

        val allGrantedResources = pending.alreadyGrantedResources + newlyGrantedResources

        if (allGrantedResources.isNotEmpty()) {
            Timber.d("Granting WebView resources: $allGrantedResources")
            pending.webViewRequest.grant(allGrantedResources.toTypedArray())
        } else {
            Timber.d("User denied all requested permissions, denying WebView request")
            pending.webViewRequest.deny()
        }
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
            PermissionRequest.RESOURCE_VIDEO_CAPTURE -> android.Manifest.permission.CAMERA
            PermissionRequest.RESOURCE_AUDIO_CAPTURE -> android.Manifest.permission.RECORD_AUDIO
            else -> {
                Timber.w("Unknown WebView permission resource: $webViewResource")
                null
            }
        }
    }
}
