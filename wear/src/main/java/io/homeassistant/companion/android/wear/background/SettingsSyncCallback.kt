package io.homeassistant.companion.android.wear.background

interface SettingsSyncCallback {
    fun onConfigReceived()
    fun onInactiveSession()
    fun onConfigSynced()
}