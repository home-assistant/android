package io.homeassistant.companion.android.wear.background

import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import io.homeassistant.companion.android.domain.authentication.Session
import io.homeassistant.companion.android.wear.BuildConfig
import io.homeassistant.companion.android.wear.background.SettingsUrl.*
import io.homeassistant.companion.android.wear.util.extensions.await
import io.homeassistant.companion.android.wear.util.extensions.catch
import javax.inject.Inject

class SettingsSyncManager @Inject constructor(
    private val messageClient: MessageClient,
    private val capabilityClient: CapabilityClient
) : MessageClient.OnMessageReceivedListener {

    private companion object {
        private const val TAG = "SettingsSyncManager"
        private const val CAPABILITY_PHONE = "verify_home_assistant_phone_app_installed"

        private const val CONFIG_PATH = "/config"

        private const val KEY_ACTIVE_SESSION = "activeSession"
        private const val KEY_URLS = "urls"
        private const val KEY_SSIDS = "ssids"
        private const val KEY_SESSION = "session"
    }

    init {
        messageClient.addListener(this)
    }

    var syncCallback: SettingsSyncCallback? = null

    suspend fun getNodeWithInstalledApp(): CapabilityResult? {
        val capabilityInfo = catch {
            capabilityClient.getCapability(CAPABILITY_PHONE, CapabilityClient.FILTER_ALL).await()
        }
        val nodes: MutableSet<Node>? = capabilityInfo?.nodes
        if (nodes == null || nodes.isEmpty()) {
            return CapabilityResult(Result.FAILURE)
        }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Nodes found: $nodes")
        }
        val foundDevice = nodes.find { node -> node.isNearby }
            ?: return CapabilityResult(Result.NOT_NEARBY)

        return CapabilityResult(Result.SUCCESS, foundDevice)
    }

    suspend fun sendMessage(nodeId: String): Boolean {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Sending message to node: $nodeId")
        }
        val result = catch { messageClient.sendMessage(nodeId, CONFIG_PATH, null).await() }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "MessageID: $result")
        }
        return result != null
    }

    override fun onMessageReceived(message: MessageEvent) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Message received from ${message.sourceNodeId}: ${message.path}")
            Log.d(TAG, "Message data: ${DataMap.fromByteArray(message.data)}")
        }
        when (message.path) {
            CONFIG_PATH -> {
                val dataMap = DataMap.fromByteArray(message.data)
                val result = when {
                    dataMap == null -> FailedSyncResult
                    !dataMap.getBoolean(KEY_ACTIVE_SESSION) -> InActiveSessionSyncResult
                    else -> {
                        val urlMap = dataMap.getDataMap(KEY_URLS)
                        val webhookId = urlMap.getString("webhook_id")
                        if (webhookId.isNullOrBlank()) {
                            FailedSyncResult
                        } else {
                            val sessionMap = dataMap.getDataMap(KEY_SESSION)
                            val session = Session(
                                accessToken = sessionMap.getString("access"),
                                expiresTimestamp = sessionMap.getLong("expires"),
                                refreshToken = sessionMap.getString("refresh"),
                                tokenType = sessionMap.getString("type")
                            )
                            val mapUrl = mapOf(
                                CLOUDHOOK to urlMap.getString("cloudhook_url"),
                                REMOTE to urlMap.getString("remote_url"),
                                LOCAL to urlMap.getString("local_url"),
                                WEBHOOK to webhookId
                            )
                            val ssids = dataMap.getStringArrayList(KEY_SSIDS)
                            SuccessSyncResult(session, mapUrl, ssids)
                        }
                    }
                }
                val callback = syncCallback ?: throw IllegalStateException("It is required to set a sync callback to be able to retrieve the result")
                callback.onSyncResult(result)
            }
        }
    }

    fun cancel() {
        messageClient.removeListener(this)
        syncCallback = null
    }

}