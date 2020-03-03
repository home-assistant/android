package io.homeassistant.companion.android.settings.ssid

import androidx.preference.Preference
import io.homeassistant.companion.android.R

class SsidSummaryProvider : Preference.SummaryProvider<SsidPreference> {

    override fun provideSummary(preference: SsidPreference): CharSequence {
        val ssids = preference.getSsids()
        if (ssids.isEmpty()) {
            return preference.context.getString(R.string.pref_connection_ssids_empty)
        }
        return ssids.joinToString(", ")
    }
}
