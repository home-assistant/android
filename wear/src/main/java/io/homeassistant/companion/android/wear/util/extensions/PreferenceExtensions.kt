package io.homeassistant.companion.android.wear.util.extensions

import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

fun <T : Preference> PreferenceFragmentCompat.requirePreference(key: String): T {
    return findPreference<T>(key) ?: throw NullPointerException("No preference found by key: $key")
}