package io.homeassistant.companion.android.settings

interface SettingsPresenter {
    fun onLocationZoneChange(value: Boolean)
    fun onLocationBackgroundChange(value: Boolean)
    fun onFinish()
}
