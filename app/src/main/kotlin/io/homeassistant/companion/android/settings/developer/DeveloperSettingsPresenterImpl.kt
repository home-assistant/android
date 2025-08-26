package io.homeassistant.companion.android.settings.developer

import android.content.Context
import android.webkit.WebStorage
import androidx.activity.result.ActivityResult
import androidx.preference.PreferenceDataStore
import androidx.webkit.WebStorageCompat
import androidx.webkit.WebViewFeature
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.thread.ThreadManager
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

class DeveloperSettingsPresenterImpl @Inject constructor(
    private val prefsRepository: PrefsRepository,
    private val serverManager: ServerManager,
    private val threadManager: ThreadManager,
) : PreferenceDataStore(),
    DeveloperSettingsPresenter {

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var view: DeveloperSettingsView

    // Cache for preferences to avoid blocking calls
    private val preferencesCache = mutableMapOf<String, Boolean>()
    private val cacheMutex = Mutex()
    private var isCacheInitialized = false

    override fun init(view: DeveloperSettingsView) {
        this.view = view

        // Initialize cache asynchronously
        mainScope.launch {
            initializePreferencesCache()
        }
    }

    override fun getPreferenceDataStore(): PreferenceDataStore = this

    override fun onFinish() {
        mainScope.cancel()
    }

    /**
     * Initialize the preferences cache to avoid blocking calls in getBoolean
     */
    private suspend fun initializePreferencesCache() {
        cacheMutex.withLock {
            if (!isCacheInitialized) {
                try {
                    preferencesCache["webview_debug"] = prefsRepository.isWebViewDebugEnabled()
                    isCacheInitialized = true
                } catch (e: Exception) {
                    Timber.e(e, "Failed to initialize preferences cache")
                }
            }
        }
    }

    /**
     * Update cache when preferences change
     */
    private fun updateCache(key: String, value: Boolean) {
        mainScope.launch {
            cacheMutex.withLock {
                preferencesCache[key] = value
            }
        }
    }

    // PreferenceDataStore methods must remain synchronous
    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        return when (key) {
            "webview_debug" -> {
                // Return cached value if available, otherwise return default
                preferencesCache[key] ?: defValue
            }
            else -> {
                Timber.w("No boolean preference found for key: $key")
                defValue
            }
        }
    }

    override fun putBoolean(key: String?, value: Boolean) {
        when (key) {
            "webview_debug" -> {
                // Update cache immediately for UI responsiveness
                updateCache(key, value)
                // Persist asynchronously
                mainScope.launch {
                    try {
                        prefsRepository.setWebViewDebugEnabled(value)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to persist webview_debug preference")
                        // Revert cache on failure
                        updateCache(key, !value)
                    }
                }
            }
            else -> {
                Timber.w("No boolean preference found for key: $key")
            }
        }
    }

    override fun hasMultipleServers(): Boolean = serverManager.defaultServers.size > 1

    override fun appSupportsThread(): Boolean = threadManager.appSupportsThread()

    override fun runThreadDebug(context: Context, serverId: Int) {
        mainScope.launch {
            try {
                val syncResult = threadManager.syncPreferredDataset(
                    context,
                    serverId,
                    false,
                    CoroutineScope(coroutineContext + SupervisorJob())
                )

                handleThreadDebugResult(syncResult, context, serverId)
            } catch (e: Exception) {
                Timber.e(e, "Exception while syncing preferred Thread dataset")
                view.onThreadDebugResult(context.getString(commonR.string.thread_debug_result_error), false)
            }
        }
    }

    // Fixed: Added serverId parameter that was missing in the original
    private fun handleThreadDebugResult(syncResult: ThreadManager.SyncResult, context: Context, serverId: Int) {
        when (syncResult) {
            is ThreadManager.SyncResult.ServerUnsupported -> view.onThreadDebugResult(
                context.getString(commonR.string.thread_debug_result_unsupported_server),
                false
            )
            is ThreadManager.SyncResult.OnlyOnServer -> {
                if (syncResult.imported) {
                    view.onThreadDebugResult(
                        context.getString(commonR.string.thread_debug_result_imported),
                        true
                    )
                } else {
                    view.onThreadDebugResult(context.getString(commonR.string.thread_debug_result_error), false)
                }
            }
            is ThreadManager.SyncResult.OnlyOnDevice -> {
                syncResult.exportIntent?.let {
                    view.onThreadPermissionRequest(it, serverId, true)
                }
            }
            is ThreadManager.SyncResult.AllHaveCredentials -> {
                handleSyncResultAllHaveCredentials(syncResult, context, serverId)
            }
            is ThreadManager.SyncResult.NoneHaveCredentials -> {
                view.onThreadDebugResult(context.getString(commonR.string.thread_debug_result_none), null)
            }
            else -> {
                view.onThreadDebugResult(context.getString(commonR.string.thread_debug_result_error), false)
            }
        }
    }

    // Fixed: Added serverId parameter that was missing in the original
    private fun handleSyncResultAllHaveCredentials(
        syncResult: ThreadManager.SyncResult.AllHaveCredentials,
        context: Context,
        serverId: Int
    ) {
        when {
            syncResult.exportIntent != null -> {
                view.onThreadPermissionRequest(syncResult.exportIntent, serverId, false)
            }
            syncResult.matches == true -> {
                view.onThreadDebugResult(context.getString(commonR.string.thread_debug_result_match), true)
            }
            syncResult.fromApp == true && syncResult.updated == true -> {
                view.onThreadDebugResult(
                    context.getString(commonR.string.thread_debug_result_updated),
                    true
                )
            }
            syncResult.fromApp == true && syncResult.updated == false -> {
                view.onThreadDebugResult(
                    context.getString(commonR.string.thread_debug_result_removed),
                    true
                )
            }
            else -> {
                view.onThreadDebugResult(context.getString(commonR.string.thread_debug_result_error), false)
            }
        }
    }

    override fun onThreadPermissionResult(
        context: Context,
        result: ActivityResult,
        serverId: Int,
        isDeviceOnly: Boolean,
    ) {
        mainScope.launch {
            try {
                val submitted = threadManager.sendThreadDatasetExportResult(result, serverId)
                if (submitted != null) {
                    if (isDeviceOnly) {
                        view.onThreadDebugResult(context.getString(commonR.string.thread_debug_result_exported), true)
                    } else {
                        val out = "${context.getString(commonR.string.thread_debug_result_mismatch)} ${context.getString(commonR.string.thread_debug_result_mismatch_detail, submitted)}"
                        view.onThreadDebugResult(out, null)
                    }
                } else {
                    view.onThreadDebugResult(context.getString(commonR.string.thread_debug_result_error), false)
                }
            } catch (e: Exception) {
                Timber.e(e, "Exception in onThreadPermissionResult")
                view.onThreadDebugResult(context.getString(commonR.string.thread_debug_result_error), false)
            }
        }
    }

    override fun webViewSupportsClearCache(): Boolean =
        WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)

    override fun clearWebViewCache() {
        if (!webViewSupportsClearCache()) return

        try {
            WebStorageCompat.deleteBrowsingData(WebStorage.getInstance(), Dispatchers.IO.asExecutor()) {
                view.onWebViewClearCacheResult(success = true)
            }
        } catch (e: RuntimeException) {
            Timber.e(e, "Unable to clear WebView cache")
            view.onWebViewClearCacheResult(success = false)
        }
    }
}