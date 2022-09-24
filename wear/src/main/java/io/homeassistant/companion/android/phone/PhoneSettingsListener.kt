package io.homeassistant.companion.android.phone

import android.content.Intent
import android.util.Log
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
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.url.UrlRepository
import io.homeassistant.companion.android.database.wear.FavoritesDao
import io.homeassistant.companion.android.database.wear.getAll
import io.homeassistant.companion.android.database.wear.replaceAll
import io.homeassistant.companion.android.home.HomeActivity
import io.homeassistant.companion.android.home.HomePresenterImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PhoneSettingsListener : WearableListenerService(), DataClient.OnDataChangedListener {

    @Inject
    lateinit var authenticationRepository: AuthenticationRepository

    @Inject
    lateinit var urlRepository: UrlRepository

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    @Inject
    lateinit var favoritesDao: FavoritesDao

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private val objectMapper = jacksonObjectMapper()

    companion object {
        private const val TAG = "PhoneSettingsListener"

        private const val KEY_UPDATE_TIME = "UpdateTime"
        private const val KEY_IS_AUTHENTICATED = "isAuthenticated"
        private const val KEY_SUPPORTED_DOMAINS = "supportedDomains"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_TEMPLATE_TILE = "templateTile"
        private const val KEY_TEMPLATE_TILE_REFRESH_INTERVAL = "templateTileRefreshInterval"
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
            dataMap.putLong(KEY_UPDATE_TIME, System.nanoTime())
            dataMap.putBoolean(KEY_IS_AUTHENTICATED, integrationUseCase.isRegistered())
            dataMap.putString(KEY_SUPPORTED_DOMAINS, objectMapper.writeValueAsString(HomePresenterImpl.supportedDomains))
            dataMap.putString(KEY_FAVORITES, objectMapper.writeValueAsString(currentFavorites))
            dataMap.putString(KEY_TEMPLATE_TILE, integrationUseCase.getTemplateTileContent())
            dataMap.putInt(KEY_TEMPLATE_TILE_REFRESH_INTERVAL, integrationUseCase.getTemplateTileRefreshInterval())
            setUrgent()
            asPutDataRequest()
        }

        Wearable.getDataClient(this@PhoneSettingsListener).putDataItem(putDataRequest).apply {
            addOnSuccessListener { Log.d(TAG, "Successfully sent /config to device") }
            addOnFailureListener { e -> Log.e(TAG, "Failed to send /config to device", e) }
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
        try {
            val url = dataMap.getString("URL", "")
            val authCode = dataMap.getString("AuthCode", "")
            val deviceName = dataMap.getString("DeviceName")
            val deviceTrackingEnabled = dataMap.getBoolean("LocationTracking")

            urlRepository.saveUrl(url)
            authenticationRepository.registerAuthorizationCode(authCode)
            integrationUseCase.registerDevice(
                DeviceRegistration(
                    "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    deviceName
                )
            )

            val intent = HomeActivity.newInstance(applicationContext)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to login to Home Assistant", e)
        }

        sendPhoneData()
    }

    private fun saveFavorites(dataMap: DataMap) {
        val favoritesIds: List<String> =
            objectMapper.readValue(dataMap.getString(KEY_FAVORITES, "[]"))

        mainScope.launch {
            favoritesDao.replaceAll(favoritesIds)
        }
    }

    private fun saveTileTemplate(dataMap: DataMap) = mainScope.launch {
        val content = dataMap.getString(KEY_TEMPLATE_TILE, "")
        val interval = dataMap.getInt(KEY_TEMPLATE_TILE_REFRESH_INTERVAL, 0)
        integrationUseCase.setTemplateTileContent(content)
        integrationUseCase.setTemplateTileRefreshInterval(interval)
    }
}
