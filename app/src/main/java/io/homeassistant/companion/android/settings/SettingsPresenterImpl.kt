package io.homeassistant.companion.android.settings

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import androidx.preference.PreferenceDataStore
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
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
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

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

    private val voiceCommandAppComponent = ComponentName(
        BuildConfig.APPLICATION_ID,
        "io.homeassistant.companion.android.assist.VoiceCommandIntentActivity"
    )

    private var suggestionFlow = MutableStateFlow<SettingsHomeSuggestion?>(null)

    override fun getBoolean(key: String, defValue: Boolean): Boolean = runBlocking {
        return@runBlocking when (key) {
            "fullscreen" -> prefsRepository.isFullScreenEnabled()
            "keep_screen_on" -> prefsRepository.isKeepScreenOnEnabled()
            "pinch_to_zoom" -> prefsRepository.isPinchToZoomEnabled()
            "crash_reporting" -> prefsRepository.isCrashReporting()
            "autoplay_video" -> prefsRepository.isAutoPlayVideoEnabled()
            "always_show_first_view_on_app_start" -> prefsRepository.isAlwaysShowFirstViewOnAppStartEnabled()
            "assist_voice_command_intent" -> {
                val componentSetting = view.getPackageManager()?.getComponentEnabledSetting(voiceCommandAppComponent)
                componentSetting != null && componentSetting != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
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
                "assist_voice_command_intent" ->
                    view.getPackageManager()?.setComponentEnabledSetting(
                        voiceCommandAppComponent,
                        if (value) PackageManager.COMPONENT_ENABLED_STATE_DEFAULT else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                else -> throw IllegalArgumentException("No boolean found by this key: $key")
            }
        }
    }

    override fun getString(key: String, defValue: String?): String? = runBlocking {
        when (key) {
            "themes" -> themesManager.getCurrentTheme()
            "languages" -> langsManager.getCurrentLang()
            "page_zoom" -> prefsRepository.getPageZoomLevel().toString()
            "screen_orientation" -> prefsRepository.getScreenOrientation()
            else -> throw IllegalArgumentException("No string found by this key: $key")
        }
    }

    override fun putString(key: String, value: String?) {
        mainScope.launch {
            when (key) {
                "themes" -> themesManager.saveTheme(value)
                "languages" -> langsManager.saveLang(value)
                "page_zoom" -> prefsRepository.setPageZoomLevel(value?.toIntOrNull())
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

    override fun getSuggestionFlow(): StateFlow<SettingsHomeSuggestion?> = suggestionFlow

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

    override fun updateSuggestions(context: Context) {
        mainScope.launch { getSuggestions(context, false) }
    }

    override fun cancelSuggestion(context: Context, id: String) {
        mainScope.launch {
            val ignored = prefsRepository.getIgnoredSuggestions()
            if (!ignored.contains(id)) {
                prefsRepository.setIgnoredSuggestions(ignored + id)
            }
            getSuggestions(context, true)
        }
    }

    private suspend fun getSuggestions(context: Context, overwrite: Boolean) {
        val suggestions = mutableListOf<SettingsHomeSuggestion>()

        // Assist
        var assistantSuggestion = serverManager.defaultServers.any { it.version?.isAtLeast(2023, 5) == true }
        assistantSuggestion = if (assistantSuggestion && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService<RoleManager>()
            roleManager?.isRoleAvailable(RoleManager.ROLE_ASSISTANT) == true && !roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)
        } else if (assistantSuggestion && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val defaultApp: String? = Settings.Secure.getString(context.contentResolver, "assistant")
            defaultApp?.contains(BuildConfig.APPLICATION_ID) == false
        } else {
            false
        }
        if (assistantSuggestion) {
            suggestions += SettingsHomeSuggestion(
                SettingsPresenter.SUGGESTION_ASSISTANT_APP,
                commonR.string.suggestion_assist_title,
                commonR.string.suggestion_assist_summary,
                R.drawable.ic_comment_processing_outline
            )
        }

        // Notifications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            suggestions += SettingsHomeSuggestion(
                SettingsPresenter.SUGGESTION_NOTIFICATION_PERMISSION,
                commonR.string.suggestion_notifications_title,
                commonR.string.suggestion_notifications_summary,
                commonR.drawable.ic_notifications
            )
        }

        val ignored = prefsRepository.getIgnoredSuggestions()
        val filteredSuggestions = suggestions.filter { !ignored.contains(it.id) }
        if (overwrite || suggestionFlow.value == null) {
            suggestionFlow.emit(filteredSuggestions.randomOrNull())
        } else if (filteredSuggestions.none { it.id == suggestionFlow.value?.id }) {
            suggestionFlow.emit(null)
        }
    }
}
