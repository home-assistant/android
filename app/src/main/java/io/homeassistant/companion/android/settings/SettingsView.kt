package io.homeassistant.companion.android.settings

interface SettingsView {

    fun disableInternalConnection()

    fun enableInternalConnection()

    fun updateExternalUrl(url: String, useCloud: Boolean)

    fun updateSsids(ssids: Set<String>)
}
