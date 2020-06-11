package io.homeassistant.companion.android.settings

interface PreferenceChangeCallback {
    fun onPreferenceChanged(key: String, value: Any?)
}
