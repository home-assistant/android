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
import kotlinx.coroutines.runBlocking
import timber.log.Timber

class DeveloperSettingsPresenterImpl @Inject constructor(
    private val prefsRepository: PrefsRepository,
    private val serverManager: ServerManager,
    private val threadManager: ThreadManager,
) : PreferenceDataStore(),
    DeveloperSettingsPresenter {

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var view: DeveloperSettingsView

    override fun init(view: DeveloperSettingsView) {
        this.view = view
    }

    override fun getPreferenceDataStore(): PreferenceDataStore = this

    override fun onFinish() {
        mainScope.cancel()
    }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean = runBlocking {
        return@runBlocking when (key) {
            "webview_debug" -> prefsRepository.isWebViewDebugEnabled()
            else -> throw IllegalArgumentException("No boolean found by this key: $key")
        }
    }

    override fun putBoolean(key: String?, value: Boolean) {
        mainScope.launch {
            when (key) {
                "webview_debug" -> prefsRepository.setWebViewDebugEnabled(value)
                else -> throw IllegalArgumentException("No boolean found by this key: $key")
            }
        }
    }

    override fun hasMultipleServers(): Boolean = serverManager.defaultServers.size > 1

    override fun appSupportsThread(): Boolean = threadManager.appSupportsThread()

    override fun runThreadDebug(context: Context, serverId: Int) {
        mainScope.launch {
            try {
                when (
                    val syncResult = threadManager.syncPreferredDataset(
                        context,
                        serverId,
                        false,
                        CoroutineScope(
                            coroutineContext + SupervisorJob(),
                        ),
                    )
                ) {
                    is ThreadManager.SyncResult.ServerUnsupported ->
                        view.onThreadDebugResult(
                            context.getString(commonR.string.thread_debug_result_unsupported_server),
                            false,
                        )
                    is ThreadManager.SyncResult.OnlyOnServer -> {
                        if (syncResult.imported) {
                            view.onThreadDebugResult(
                                context.getString(commonR.string.thread_debug_result_imported),
                                true,
                            )
                        } else {
                            view.onThreadDebugResult(context.getString(commonR.string.thread_debug_result_error), false)
                        }
                    }
                    is ThreadManager.SyncResult.OnlyOnDevice -> {
                        if (syncResult.exportIntent != null) {
                            view.onThreadPermissionRequest(syncResult.exportIntent, serverId, true)
                        } // else currently doesn't happen
                    }
                    is ThreadManager.SyncResult.AllHaveCredentials -> {
                        if (syncResult.exportIntent != null) {
                            view.onThreadPermissionRequest(syncResult.exportIntent, serverId, false)
                        } else if (syncResult.matches == true) {
                            view.onThreadDebugResult(context.getString(commonR.string.thread_debug_result_match), true)
                        } else if (syncResult.fromApp == true && syncResult.updated == true) {
                            view.onThreadDebugResult(
                                context.getString(commonR.string.thread_debug_result_updated),
                                true,
                            )
                        } else if (syncResult.fromApp == true && syncResult.updated == false) {
                            view.onThreadDebugResult(
                                context.getString(commonR.string.thread_debug_result_removed),
                                true,
                            )
                        } else {
                            view.onThreadDebugResult(context.getString(commonR.string.thread_debug_result_error), false)
                        }
                    }
                    is ThreadManager.SyncResult.NoneHaveCredentials ->
                        view.onThreadDebugResult(context.getString(commonR.string.thread_debug_result_none), null)
                    else ->
                        view.onThreadDebugResult(context.getString(commonR.string.thread_debug_result_error), false)
                }
            } catch (e: Exception) {
                Timber.e(e, "Exception while syncing preferred Thread dataset")
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
                        // If we got permission while both had a dataset, the device prefers a different network
                        val out = "${context.getString(
                            commonR.string.thread_debug_result_mismatch,
                        )} ${context.getString(commonR.string.thread_debug_result_mismatch_detail, submitted)}"
                        view.onThreadDebugResult(out, null)
                    }
                } else {
                    view.onThreadDebugResult(context.getString(commonR.string.thread_debug_result_error), false)
                }
            } catch (e: Exception) {
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
