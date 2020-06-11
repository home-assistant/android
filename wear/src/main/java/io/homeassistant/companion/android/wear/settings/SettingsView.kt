package io.homeassistant.companion.android.wear.settings

interface SettingsView {
    fun displaySyncInProgress(inProgress: Boolean)
    fun showConfirmed(confirmedType: Int, message: Int)
    fun onLocationSettingChanged()
    fun onSensorSettingChanged(value: Boolean)
}
