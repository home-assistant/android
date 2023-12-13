package io.homeassistant.companion.android.phone

import android.annotation.SuppressLint
import android.content.Intent
import android.util.Log
import androidx.wear.tiles.TileService
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.keychain.KeyChainRepository
import io.homeassistant.companion.android.common.data.keychain.KeyStoreRepositoryImpl
import io.homeassistant.companion.android.common.data.prefs.WearPrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.WearDataMessages
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerType
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.homeassistant.companion.android.database.wear.FavoritesDao
import io.homeassistant.companion.android.database.wear.getAll
import io.homeassistant.companion.android.database.wear.replaceAll
import io.homeassistant.companion.android.home.HomeActivity
import io.homeassistant.companion.android.home.HomePresenterImpl
import io.homeassistant.companion.android.onboarding.getMessagingToken
import io.homeassistant.companion.android.tiles.CameraTile
import io.homeassistant.companion.android.tiles.ConversationTile
import io.homeassistant.companion.android.tiles.ShortcutsTile
import io.homeassistant.companion.android.tiles.TemplateTile
import io.homeassistant.companion.android.util.UrlUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Named

@AndroidEntryPoint
@SuppressLint("VisibleForTests") // https://issuetracker.google.com/issues/239451111
class PhoneSettingsListener : WearableListenerService(), DataClient.OnDataChangedListener {

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var wearPrefsRepository: WearPrefsRepository

    @Inject
    lateinit var favoritesDao: FavoritesDao

    @Inject
    @Named("keyChainRepository")
    lateinit var keyChainRepository: KeyChainRepository

    @Inject
    @Named("keyStore")
    lateinit var keyStore: KeyChainRepository

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private val objectMapper = jacksonObjectMapper()

    companion object {
        private const val TAG = "PhoneSettingsListener"
    }

    override fun onMessageReceived(event: MessageEvent) {
        Log.d(TAG, "Message received: $event")
        if (event.path == "/requestConfig") {
            sendPhoneData()
        }
    }

    private fun sendPhoneData() = mainScope.launch {
        val currentFavorites = favoritesDao.getAll()
        val putDataRequest = PutDataMapRequest.create("/config").run {
            dataMap.putLong(WearDataMessages.KEY_UPDATE_TIME, System.nanoTime())
            val isRegistered = serverManager.isRegistered()
            dataMap.putBoolean(WearDataMessages.CONFIG_IS_AUTHENTICATED, isRegistered)
            if (isRegistered) {
                dataMap.putInt(WearDataMessages.CONFIG_SERVER_ID, serverManager.getServer()?.id ?: 0)
                dataMap.putString(WearDataMessages.CONFIG_SERVER_EXTERNAL_URL, serverManager.getServer()?.connection?.externalUrl ?: "")
                dataMap.putString(WearDataMessages.CONFIG_SERVER_WEBHOOK_ID, serverManager.getServer()?.connection?.webhookId ?: "")
                dataMap.putString(WearDataMessages.CONFIG_SERVER_CLOUD_URL, serverManager.getServer()?.connection?.cloudUrl ?: "")
                dataMap.putString(WearDataMessages.CONFIG_SERVER_CLOUDHOOK_URL, serverManager.getServer()?.connection?.cloudhookUrl ?: "")
                dataMap.putBoolean(WearDataMessages.CONFIG_SERVER_USE_CLOUD, serverManager.getServer()?.connection?.useCloud ?: false)
                dataMap.putString(WearDataMessages.CONFIG_SERVER_REFRESH_TOKEN, serverManager.getServer()?.session?.refreshToken ?: "")
            }
            dataMap.putString(WearDataMessages.CONFIG_SUPPORTED_DOMAINS, objectMapper.writeValueAsString(HomePresenterImpl.supportedDomains))
            dataMap.putString(WearDataMessages.CONFIG_FAVORITES, objectMapper.writeValueAsString(currentFavorites))
            dataMap.putString(WearDataMessages.CONFIG_TEMPLATE_TILE, wearPrefsRepository.getTemplateTileContent())
            dataMap.putInt(WearDataMessages.CONFIG_TEMPLATE_TILE_REFRESH_INTERVAL, wearPrefsRepository.getTemplateTileRefreshInterval())
            setUrgent()
            asPutDataRequest()
        }

        try {
            Wearable.getDataClient(this@PhoneSettingsListener).putDataItem(putDataRequest).await()
            Log.d(TAG, "Successfully sent /config to device")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send /config to device", e)
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d(TAG, "onDataChanged ${dataEvents.count}")
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                event.dataItem.also { item ->
                    when (item.uri.path) {
                        "/authenticate" -> {
                            login(DataMapItem.fromDataItem(item).dataMap)
                        }
                        "/updateFavorites" -> {
                            saveFavorites(DataMapItem.fromDataItem(item).dataMap)
                        }
                        "/updateTemplateTile" -> {
                            saveTileTemplate(DataMapItem.fromDataItem(item).dataMap)
                        }
                    }
                }
            }
        }
        dataEvents.release()
    }

    private fun login(dataMap: DataMap) = mainScope.launch {
        var authId = ""
        var serverId: Int? = null
        try {
            authId = dataMap.getString("AuthId", "")
            val url = dataMap.getString("URL", "")
            val authCode = dataMap.getString("AuthCode", "")
            val deviceName = dataMap.getString("DeviceName")
            val deviceTrackingEnabled = dataMap.getBoolean("LocationTracking")
            val notificationsEnabled = dataMap.getBoolean("Notifications")
            val tlsClientCertificateData = dataMap.getByteArray("TLSClientCertificateData")
            val tlsClientCertificatePassword = dataMap.getString("TLSClientCertificatePassword").orEmpty().toCharArray()

            // load TLS key
            if (tlsClientCertificateData != null && tlsClientCertificateData.isNotEmpty()) {
                KeyStore.getInstance("PKCS12").apply {
                    load(tlsClientCertificateData.inputStream(), tlsClientCertificatePassword)

                    val alias = aliases().nextElement()
                    val certificateChain = getCertificateChain(alias).filterIsInstance<X509Certificate>().toTypedArray()
                    val privateKey = getKey(alias, tlsClientCertificatePassword) as PrivateKey

                    // we store the TLS Client key under a static alias because there is currently
                    // no way to ask the user for the correct alias
                    keyStore.setData(KeyStoreRepositoryImpl.ALIAS, privateKey, certificateChain)
                    keyChainRepository.load(applicationContext)
                }
            }

            val formattedUrl = UrlUtil.formattedUrlString(url)
            val server = Server(
                _name = "",
                type = ServerType.TEMPORARY,
                connection = ServerConnectionInfo(
                    externalUrl = formattedUrl
                ),
                session = ServerSessionInfo(),
                user = ServerUserInfo()
            )
            serverId = serverManager.addServer(server)
            serverManager.authenticationRepository(serverId).registerAuthorizationCode(authCode)
            serverManager.integrationRepository(serverId).registerDevice(
                DeviceRegistration(
                    "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    deviceName,
                    getMessagingToken(),
                    false
                )
            )
            serverManager.convertTemporaryServer(serverId)
            launch {
                sendLoginResult(authId, true, null)
                updateTiles()
            }

            val intent = HomeActivity.newInstance(applicationContext)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to login to Home Assistant", e)
            try {
                if (serverId != null) {
                    serverManager.authenticationRepository(serverId).revokeSession()
                    serverManager.removeServer(serverId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Can't revoke session", e)
            }
            launch {
                sendLoginResult(authId, false, e.stackTraceToString())
            }
        }

        sendPhoneData()
    }

    private suspend fun sendLoginResult(id: String?, success: Boolean, exception: String?) {
        try {
            val putDataRequest = PutDataMapRequest.create(WearDataMessages.PATH_LOGIN_RESULT).run {
                dataMap.putString(WearDataMessages.KEY_ID, id ?: "")
                dataMap.putBoolean(WearDataMessages.KEY_SUCCESS, success)
                if (exception != null) {
                    dataMap.putString(WearDataMessages.LOGIN_RESULT_EXCEPTION, exception)
                }
                setUrgent()
                asPutDataRequest()
            }
            Wearable.getDataClient(this@PhoneSettingsListener).putDataItem(putDataRequest).await()
            Log.d(TAG, "Successfully sent ${WearDataMessages.PATH_LOGIN_RESULT} to device")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send ${WearDataMessages.PATH_LOGIN_RESULT} to device", e)
        }
    }

    private fun saveFavorites(dataMap: DataMap) {
        val favoritesIds: List<String> =
            objectMapper.readValue(dataMap.getString(WearDataMessages.CONFIG_FAVORITES, "[]"))

        mainScope.launch {
            favoritesDao.replaceAll(favoritesIds)

            if (favoritesIds.isEmpty() && wearPrefsRepository.getWearFavoritesOnly()) {
                wearPrefsRepository.setWearFavoritesOnly(false)
            }
        }
    }

    private fun saveTileTemplate(dataMap: DataMap) = mainScope.launch {
        val content = dataMap.getString(WearDataMessages.CONFIG_TEMPLATE_TILE, "")
        val interval = dataMap.getInt(WearDataMessages.CONFIG_TEMPLATE_TILE_REFRESH_INTERVAL, 0)
        wearPrefsRepository.setTemplateTileContent(content)
        wearPrefsRepository.setTemplateTileRefreshInterval(interval)
    }

    private fun updateTiles() = mainScope.launch {
        try {
            val updater = TileService.getUpdater(applicationContext)
            updater.requestUpdate(CameraTile::class.java)
            updater.requestUpdate(ConversationTile::class.java)
            updater.requestUpdate(ShortcutsTile::class.java)
            updater.requestUpdate(TemplateTile::class.java)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to request tiles update")
        }
    }
}
