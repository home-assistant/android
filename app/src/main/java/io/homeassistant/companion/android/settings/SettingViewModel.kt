package io.homeassistant.companion.android.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.settings.SensorUpdateFrequencySetting
import io.homeassistant.companion.android.database.settings.Setting
import io.homeassistant.companion.android.database.settings.WebsocketSetting
import io.homeassistant.companion.android.websocket.WebsocketManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SettingViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(
    application
) {
    private val settingsDao = AppDatabase.getInstance(application).settingsDao()

    fun getSetting(id: Int): Setting {
        var setting = settingsDao.get(id)
        if (setting == null) {
            setting = Setting(id, if (BuildConfig.FLAVOR == "full") WebsocketSetting.NEVER else WebsocketSetting.ALWAYS, SensorUpdateFrequencySetting.NORMAL)
            settingsDao.insert(setting)
        }
        return setting
    }

    // Once we support more than one instance we can get the setting per instance
    fun getSettingFlow(id: Int): Flow<Setting> = settingsDao.getFlow(id)

    fun updateWebsocketSetting(id: Int, setting: WebsocketSetting) {
        settingsDao.get(id)?.let {
            it.websocketSetting = setting
            settingsDao.update(it)
        }
        WebsocketManager.start(getApplication())
    }

    fun updateSensorSetting(id: Int, setting: SensorUpdateFrequencySetting) {
        settingsDao.get(id)?.let {
            it.sensorUpdateFrequency = setting
            settingsDao.update(it)
        }
    }
}
