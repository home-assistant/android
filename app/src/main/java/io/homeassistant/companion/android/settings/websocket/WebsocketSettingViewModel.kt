package io.homeassistant.companion.android.settings.websocket

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.settings.Setting
import io.homeassistant.companion.android.database.settings.WebsocketSetting
import io.homeassistant.companion.android.websocket.WebsocketManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class WebsocketSettingViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(
    application
) {
    private val settingsDao = AppDatabase.getInstance(application).settingsDao()

    fun getWebsocketSetting(id: Int): Setting {
        var setting = settingsDao.get(id)
        if (setting == null) {
            setting = Setting(id, if (BuildConfig.FLAVOR == "full") WebsocketSetting.SCREEN_ON else WebsocketSetting.ALWAYS)
            settingsDao.insert(setting)
        }
        return setting
    }

    // Once we support more than one instance we can get the setting per instance
    fun getWebsocketSettingFlow(id: Int): Flow<Setting> = settingsDao.getFlow(id)

    fun updateWebsocketSetting(id: Int, setting: WebsocketSetting) {
        settingsDao.get(id)?.let {
            it.websocketSetting = setting
            settingsDao.update(it)
        }
        WebsocketManager.start(getApplication())
    }
}
