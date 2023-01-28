package io.homeassistant.companion.android.settings.server

interface ServerSettingsView {
    fun enableInternalConnection(isEnabled: Boolean)
    fun updateExternalUrl(url: String, useCloud: Boolean)
    fun updateSsids(ssids: Set<String>)
}
