package io.homeassistant.companion.android.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.database.settings.SensorUpdateFrequencySetting
import io.homeassistant.companion.android.database.settings.Setting
import io.homeassistant.companion.android.database.settings.SettingsDao
import io.homeassistant.companion.android.database.settings.WebsocketSetting
import io.homeassistant.companion.android.websocket.WebsocketManager
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

@HiltViewModel
class SettingViewModel @Inject constructor(
    private val settingsDao: SettingsDao,
    application: Application
) : AndroidViewModel(application) {
    fun getSetting(serverId: Int): Setting {
        var setting = settingsDao.get(serverId)
        if (setting == null) {
            setting = Setting(
                serverId,
                if (BuildConfig.FLAVOR == "full") WebsocketSetting.NEVER else WebsocketSetting.ALWAYS,
                SensorUpdateFrequencySetting.NORMAL,
                null,
            )
            settingsDao.insert(setting)
        }
        return setting
    }

    fun getSettingFlow(serverId: Int): Flow<Setting> = settingsDao.getFlow(serverId)

    fun updateWebsocketSetting(serverId: Int, setting: WebsocketSetting) {
        settingsDao.get(serverId)?.let {
            it.websocketSetting = setting
            settingsDao.update(it)
        }
        WebsocketManager.start(getApplication())
    }

    fun updateSensorSetting(serverId: Int, setting: SensorUpdateFrequencySetting) {
        settingsDao.get(serverId)?.let {
            it.sensorUpdateFrequency = setting
            settingsDao.update(it)
        }
    }
}
