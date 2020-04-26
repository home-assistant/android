package io.homeassistant.companion.android.wear.background

import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import io.homeassistant.companion.android.domain.authentication.AuthenticationUseCase
import io.homeassistant.companion.android.domain.authentication.Session
import io.homeassistant.companion.android.domain.url.UrlUseCase
import io.homeassistant.companion.android.wear.BuildConfig
import io.homeassistant.companion.android.wear.util.extensions.await
import io.homeassistant.companion.android.wear.util.extensions.catch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SettingsSyncManager @Inject constructor(
    private val messageClient: MessageClient,
    private val capabilityClient: CapabilityClient,
    private val authenticationUseCase: AuthenticationUseCase,
    private val urlUseCase: UrlUseCase
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

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    var syncCallback: SettingsSyncCallback? = null

    suspend fun getNodeWithInstalledApp(): CapabilityResult? {
        val capabilityInfo = catch {
            capabilityClient.getCapability(CAPABILITY_PHONE, CapabilityClient.FILTER_ALL).await()
        }
        val nodes: MutableSet<Node>? = capabilityInfo?.nodes
        if (nodes == null || nodes.isEmpty()) {
            return CapabilityResult(Result.FAILURE)
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
                syncCallback?.onConfigReceived()
                val dataMap = DataMap.fromByteArray(message.data)
                if (!dataMap.getBoolean(KEY_ACTIVE_SESSION)) {
                    syncCallback?.onInactiveSession()
                    return
                }
                ioScope.launch {
                    val sessionMap = dataMap.getDataMap(KEY_SESSION)
                    val session = Session(
                        accessToken = sessionMap.getString("access"),
                        expiresTimestamp = sessionMap.getLong("expires"),
                        refreshToken = sessionMap.getString("refresh"),
                        tokenType = sessionMap.getString("type")
                    )
                    authenticationUseCase.saveSession(session)

                    val ssids = dataMap.getStringArrayList(KEY_SSIDS).toSet()
                    urlUseCase.saveHomeWifiSsids(ssids)

                    val urlMap = dataMap.getDataMap(KEY_URLS)
                    val cloudUrl = urlMap.getString("cloudhook_url")
                    val remoteUrl = urlMap.getString("remote_url")
                    val localUrl = urlMap.getString("local_url")
                    val webhookId = urlMap.getString("webhook_id")
                    urlUseCase.saveRegistrationUrls(cloudUrl, remoteUrl, webhookId, localUrl)
                    withContext(Dispatchers.Main) { syncCallback?.onConfigSynced() }
                }
            }
        }
    }

    fun cancel() {
        messageClient.removeListener(this)
        ioScope.cancel()
        syncCallback = null
    }

}