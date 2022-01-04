package io.homeassistant.companion.android.settings.ssid

import androidx.preference.Preference
import io.homeassistant.companion.android.common.R as commonR

class SsidSummaryProvider : Preference.SummaryProvider<SsidPreference> {

    override fun provideSummary(preference: SsidPreference): CharSequence {
        val ssids = preference.getSsids()
        if (ssids.isEmpty()) {
            return preference.context.getString(commonR.string.pref_connection_ssids_empty)
        }
        return ssids.joinToString(", ")
    }
}
