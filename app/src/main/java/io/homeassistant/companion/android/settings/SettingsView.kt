package io.homeassistant.companion.android.settings

interface SettingsView {
    fun onLocationSettingChanged()

    fun onUrlChanged()

    fun disableInternalConnection()

    fun enableInternalConnection()
}
