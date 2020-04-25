package io.homeassistant.companion.android.wear.settings

interface SettingsPresenter {
    fun onViewReady()
    fun syncSettings()
    fun finish()
}