package io.homeassistant.companion.android.settings

interface SettingsView {
    fun onLocationSettingChanged()

    fun disableInternalConnection()

    fun enableInternalConnection()
}
