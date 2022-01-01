package io.homeassistant.companion.android.settings.notification

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.settings.LocalNotificationSetting
import io.homeassistant.companion.android.database.settings.Setting
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class LocalNotificationViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(
    application
) {
    private val settingsDao = AppDatabase.getInstance(application).settingsDao()

    fun getLocalNotificationSetting(id: Int): Setting {
        var setting = settingsDao.get(id)
        if (setting == null) {
            setting = Setting(id, LocalNotificationSetting.SCREEN_ON)
            settingsDao.insert(setting)
        }
        return setting
    }

    // Once we support more than one instance we can get the setting per instance
    fun getLocalNotificationSettingFlow(id: Int): Flow<Setting> = settingsDao.getFlow(id)

    fun updateLocalNotificationSetting(id: Int, setting: LocalNotificationSetting) {
        settingsDao.get(id)?.let {
            it.localNotificationSetting = setting
            settingsDao.update(it)
        }
    }
}
