package io.homeassistant.companion.android.settings.server

interface ServerSettingsView {
    fun updateServerName(name: String)
    fun enableInternalConnection(isEnabled: Boolean)
    fun updateExternalUrl(url: String, useCloud: Boolean)
    fun updateHomeNetwork(ssids: List<String>, ethernet: Boolean?, vpn: Boolean?)
    fun onRemovedServer(success: Boolean, hasAnyRemaining: Boolean)
}
