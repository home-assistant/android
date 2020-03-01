package io.homeassistant.companion.android.settings.ssid

import androidx.preference.Preference
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.domain.url.UrlUseCase
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

class SsidSummaryProvider @Inject constructor(
    private val urlUseCase: UrlUseCase
) : Preference.SummaryProvider<Preference> {

    override fun provideSummary(preference: Preference): CharSequence {
        val ssids = runBlocking { urlUseCase.getHomeWifiSsids() }
        if (ssids.isEmpty()) {
            return preference.context.getString(R.string.pref_connection_ssids_empty)
        }
        return ssids.joinToString(", ")
    }
}
