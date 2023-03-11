package io.homeassistant.companion.android.settings

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceDataStore
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.integration.impl.entities.RateLimitResponse
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.sensor.SensorDao
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerType
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.homeassistant.companion.android.database.settings.SensorUpdateFrequencySetting
import io.homeassistant.companion.android.database.settings.Setting
import io.homeassistant.companion.android.database.settings.SettingsDao
import io.homeassistant.companion.android.database.settings.WebsocketSetting
import io.homeassistant.companion.android.onboarding.OnboardApp
import io.homeassistant.companion.android.onboarding.getMessagingToken
import io.homeassistant.companion.android.sensors.LocationSensorManager
import io.homeassistant.companion.android.settings.language.LanguagesManager
import io.homeassistant.companion.android.themes.ThemesManager
import io.homeassistant.companion.android.util.ChangeLog
import io.homeassistant.companion.android.util.UrlUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SettingsPresenterImpl @Inject constructor(
    private val serverManager: ServerManager,
    private val prefsRepository: PrefsRepository,
    private val themesManager: ThemesManager,
    private val langsManager: LanguagesManager,
    private val changeLog: ChangeLog,
    private val settingsDao: SettingsDao,
    private val sensorDao: SensorDao
) : SettingsPresenter, PreferenceDataStore() {

    companion object {
        private const val TAG = "SettingsPresenter"
    }

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private lateinit var view: SettingsView

    override fun getBoolean(key: String, defValue: Boolean): Boolean = runBlocking {
        return@runBlocking when (key) {
            "fullscreen" -> prefsRepository.isFullScreenEnabled()
            "keep_screen_on" -> prefsRepository.isKeepScreenOnEnabled()
            "pinch_to_zoom" -> prefsRepository.isPinchToZoomEnabled()
            "crash_reporting" -> prefsRepository.isCrashReporting()
            "autoplay_video" -> prefsRepository.isAutoPlayVideoEnabled()
            "always_show_first_view_on_app_start" -> prefsRepository.isAlwaysShowFirstViewOnAppStartEnabled()
            "webview_debug" -> prefsRepository.isWebViewDebugEnabled()
            else -> throw IllegalArgumentException("No boolean found by this key: $key")
        }
    }

    override fun putBoolean(key: String, value: Boolean) {
        mainScope.launch {
            when (key) {
                "fullscreen" -> prefsRepository.setFullScreenEnabled(value)
                "keep_screen_on" -> prefsRepository.setKeepScreenOnEnabled(value)
                "pinch_to_zoom" -> prefsRepository.setPinchToZoomEnabled(value)
                "crash_reporting" -> prefsRepository.setCrashReporting(value)
                "autoplay_video" -> prefsRepository.setAutoPlayVideo(value)
                "always_show_first_view_on_app_start" -> prefsRepository.setAlwaysShowFirstViewOnAppStart(value)
                "webview_debug" -> prefsRepository.setWebViewDebugEnabled(value)
                else -> throw IllegalArgumentException("No boolean found by this key: $key")
            }
        }
    }

    override fun getString(key: String, defValue: String?): String? = runBlocking {
        when (key) {
            "themes" -> themesManager.getCurrentTheme()
            "languages" -> langsManager.getCurrentLang()
            "screen_orientation" -> prefsRepository.getScreenOrientation()
            else -> throw IllegalArgumentException("No string found by this key: $key")
        }
    }

    override fun putString(key: String, value: String?) {
        mainScope.launch {
            when (key) {
                "themes" -> themesManager.saveTheme(value)
                "languages" -> langsManager.saveLang(value)
                "screen_orientation" -> prefsRepository.saveScreenOrientation(value)
                else -> throw IllegalArgumentException("No string found by this key: $key")
            }
        }
    }

    override fun init(view: SettingsView) {
        this.view = view
    }

    override fun getPreferenceDataStore(): PreferenceDataStore {
        return this
    }

    override fun onFinish() {
        mainScope.cancel()
    }

    override fun getServersFlow(): StateFlow<List<Server>> = serverManager.defaultServersFlow

    override fun getServerCount(): Int = serverManager.defaultServers.size

    override suspend fun getNotificationRateLimits(): RateLimitResponse? = withContext(Dispatchers.IO) {
        try {
            if (serverManager.isRegistered()) {
                serverManager.integrationRepository().getNotificationRateLimits()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "Unable to get rate limits")
            return@withContext null
        }
    }

    override fun showChangeLog(context: Context) {
        changeLog.showChangeLog(context, true)
    }

    override suspend fun addServer(result: OnboardApp.Output?) {
        if (result != null) {
            val (url, authCode, deviceName, deviceTrackingEnabled, notificationsEnabled) = result
            val messagingToken = getMessagingToken()
            var serverId: Int? = null
            try {
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
                        messagingToken
                    )
                )
                serverManager.getServer()?.id?.let {
                    serverManager.activateServer(it) // Prevent unexpected active server changes
                }
                serverId = serverManager.convertTemporaryServer(serverId)
                serverId?.let {
                    setLocationTracking(it, deviceTrackingEnabled)
                    setNotifications(it, notificationsEnabled)
                }
                view.onAddServerResult(true, serverId)
            } catch (e: Exception) {
                Log.e(TAG, "Exception while registering", e)
                try {
                    if (serverId != null) {
                        serverManager.authenticationRepository(serverId).revokeSession()
                        serverManager.removeServer(serverId)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Can't revoke session", e)
                }
                view.onAddServerResult(false, null)
            }
        }
    }

    private suspend fun setLocationTracking(serverId: Int, enabled: Boolean) {
        sensorDao.setSensorsEnabled(
            sensorIds = listOf(
                LocationSensorManager.backgroundLocation.id,
                LocationSensorManager.zoneLocation.id,
                LocationSensorManager.singleAccurateLocation.id
            ),
            serverId = serverId,
            enabled = enabled
        )
    }

    private fun setNotifications(serverId: Int, enabled: Boolean) {
        // Full: this only refers to the system permission on Android 13+ so no changes are necessary.
        // Minimal: change persistent connection setting to reflect preference.
        if (BuildConfig.FLAVOR != "full") {
            settingsDao.insert(
                Setting(
                    serverId,
                    if (enabled) WebsocketSetting.ALWAYS else WebsocketSetting.NEVER,
                    SensorUpdateFrequencySetting.NORMAL
                )
            )
        }
    }
}
