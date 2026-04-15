package io.homeassistant.companion.android.frontend.permissions

import android.annotation.SuppressLint
import android.os.Build
import android.webkit.PermissionRequest as WebViewPermissionRequest
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Centralizes all Android runtime permission request/result for the frontend screen.
 *
 * ## How it works
 *
 * All permission requests flow through a single [pendingPermissionRequest] state.
 * The UI observes this flow and decides what to do with it.
 *
 * Requests are queued: if a request is already in-flight, new requests suspend until the
 * current one is resolved or dismissed via [clearPendingPermissionRequest].
 *
 * ## Typical flow
 *
 * 1. A trigger (WebView callback, frontend connect, download) calls one of the `check*` / `on*` methods
 * 2. The method creates a [PermissionRequest] and emits it (suspending if one is already active)
 * 3. The observer reacts to the new request
 * 4. Once the user has responded, [clearPendingPermissionRequest] is called (freeing the slot
 *    for the next queued request) followed by [PermissionRequest.onResult] to let the request handle its result
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
     * request is active at a time. [clear] frees the slot and unblocks the next waiting caller.
     */
    @OptIn(ExperimentalForInheritanceCoroutinesApi::class)
    private class PendingPermissionManager(
        private val _state: MutableStateFlow<PermissionRequest<*>?> = MutableStateFlow(null),
    ) : StateFlow<PermissionRequest<*>?> by _state.asStateFlow() {
        private val mutex = Mutex()
        suspend fun emit(request: PermissionRequest<*>) {
            mutex.withLock {
                // Wait for the slot to be free, then fill it atomically.
                // The mutex ensures only one caller can observe null and set a value,
                // preventing concurrent emitters from overwriting each other.
                _state.first { it == null }
                _state.value = request
            }
        }

        fun clear() {
            _state.value = null
        }
    }

    private val _pendingPermissionRequest = PendingPermissionManager()

    /** The current pending permission request that needs user approval, or null if none. */
    val pendingPermissionRequest: StateFlow<PermissionRequest<*>?> = _pendingPermissionRequest

    /**
     * Checks whether the notification permission request should be made and, if so,
     * enqueues a [PermissionRequest.Notification].
     *
     * Does nothing on pre-TIRAMISU devices (POST_NOTIFICATIONS does not exist).
     *
     * Suspends if another permission request is already in-flight, and resumes once
     * the slot is free.
     *
     * Whether to request depends on the flavor:
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

        _pendingPermissionRequest.emit(
            PermissionRequest.Notification(
                serverId = serverId,
                persistResult = { granted -> onNotificationPermissionResult(serverId = serverId, granted = granted) },
            ),
        )
    }

    /**
     * Checks whether the storage permission request should be made for a download and, if so,
     * enqueues a [PermissionRequest.ExternalStorage].
     *
     * Does nothing on API 29+ (scoped storage) or when the permission is already granted.
     *
     * Suspends if another permission request is already in-flight, and resumes once
     * the slot is free.
     *
     * @param onGranted Callback invoked after the user grants the permission, typically
     *        used to retry the download that was blocked
     * @return `true` if the download must wait for permission; `false` if it can proceed now
     */
    suspend fun checkStoragePermissionForDownload(onGranted: () -> Unit): Boolean {
        if (sdkInt >= Build.VERSION_CODES.Q) return false
        if (permissionChecker.hasPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)) return false

        Timber.d("Storage permission required for download, deferring until granted")
        _pendingPermissionRequest.emit(
            PermissionRequest.ExternalStorage(onGranted = onGranted),
        )
        return true
    }

    /**
     * Processes a WebView permission request (camera, microphone).
     *
     * Resources for which the app already holds Android runtime permissions are collected
     * and deferred so they can be granted together with any newly-approved resources in a
     * single [WebViewPermissionRequest.grant] call.
     *
     * If permissions still need to be requested, a [PermissionRequest.WebView] is enqueued.
     * Suspends if another permission request is already in-flight, and resumes once
     * the slot is free.
     *
     * @param request The [WebViewPermissionRequest] from the WebView's `onPermissionRequest` callback
     */
    suspend fun onWebViewPermissionRequest(request: WebViewPermissionRequest?) {
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
                PermissionRequest.WebView(
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
     * Clears the current pending permission request.
     *
     * Called by the observer in two scenarios:
     * - Before delivering a result via [PermissionRequest.onResult] (freeing the slot for queued requests)
     * - When the user dismisses the request without making an explicit choice, in which case
     *   the request may reappear on the next trigger
     *
     * Must only be called when a request is in-flight; triggers [FailFast] otherwise.
     */
    fun clearPendingPermissionRequest() {
        FailFast.failWhen(_pendingPermissionRequest.value == null) {
            "Should have in-flight permission while calling clearPendingPermissionRequest"
        }
        _pendingPermissionRequest.clear()
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
