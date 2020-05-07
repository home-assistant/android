package io.homeassistant.companion.android.background

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.core.os.bundleOf
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.domain.authentication.AuthenticationUseCase
import io.homeassistant.companion.android.domain.url.UrlUseCase
import io.homeassistant.companion.android.util.extensions.catch
import io.homeassistant.companion.android.util.extensions.isAbsoluteUrl
import io.homeassistant.companion.android.webview.WebViewActivity
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

class WearDataListenerService : WearableListenerService() {

    private companion object {
        private const val TAG = "WearDataListenerService"

        private const val PATH_CONFIG = "/config"
        private const val PATH_ACTION = "/action"
    }

    @Inject lateinit var authenticationUseCase: AuthenticationUseCase
    @Inject lateinit var urlUseCase: UrlUseCase

    override fun onCreate() {
        super.onCreate()
        val graphAccessor = applicationContext as GraphComponentAccessor
        DaggerServiceComponent.factory().create(graphAccessor.appComponent).inject(this)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) = runBlocking {
        when (messageEvent.path) {
            PATH_CONFIG -> {
                val session = authenticationUseCase.retrieveSession()
                val urls = urlUseCase.getBaseApiUrls()
                val ssids = urlUseCase.getHomeWifiSsids()

                val bundle = bundleOf()
                if (session == null || urls.isEmpty()) {
                    bundle.putBoolean("activeSession", false)
                } else {
                    bundle.putBoolean("activeSession", true)
                    bundle.putBundle("urls", Bundle().apply {
                        urls.forEach { (key, url) -> putString(key, url) }
                    })
                    bundle.putStringArrayList("ssids", ArrayList(ssids))
                    bundle.putBundle("session", bundleOf(
                        "access" to session.accessToken,
                        "expires" to session.expiresTimestamp,
                        "refresh" to session.refreshToken,
                        "type" to session.tokenType
                    ))
                }
                replyMessage(messageEvent.sourceNodeId, PATH_CONFIG, DataMap.fromBundle(bundle))
            }
            PATH_ACTION -> {
                val dataMap = catch { DataMap.fromByteArray(messageEvent.data) }
                if (dataMap == null) {
                    Log.e(TAG, "Path: ${messageEvent.path} - Unable to parse data")
                    return@runBlocking
                }
                val actionUrl = dataMap.getString("ActionUri")
                val intent = if (actionUrl.isAbsoluteUrl()) {
                    Intent(Intent.ACTION_VIEW).setData(Uri.parse(actionUrl))
                } else {
                    WebViewActivity.newInstance(this@WearDataListenerService, actionUrl)
                }
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                startActivity(intent)
            }
        }
    }

    private fun replyMessage(nodeId: String, path: String, dataMap: DataMap) {
        val messageClient = Wearable.getMessageClient(this)
        val dataByteArray = dataMap.toByteArray()
        messageClient.sendMessage(nodeId, path, dataByteArray)
    }

}