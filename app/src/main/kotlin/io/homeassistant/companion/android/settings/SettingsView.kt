package io.homeassistant.companion.android.settings

import android.content.pm.PackageManager

interface SettingsView {
    fun onAddServerResult(success: Boolean, serverId: Int?)
    fun getPackageManager(): PackageManager?
}
