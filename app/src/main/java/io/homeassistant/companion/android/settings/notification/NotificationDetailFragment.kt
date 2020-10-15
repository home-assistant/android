package io.homeassistant.companion.android.settings.notification

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.database.notification.NotificationItem
import java.util.Calendar
import java.util.GregorianCalendar

class NotificationDetailFragment(
    private val notification: NotificationItem
) :
    PreferenceFragmentCompat() {

    companion object {
        fun newInstance(
            notification: NotificationItem
        ): NotificationDetailFragment {
            return NotificationDetailFragment(notification)
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

        addPreferencesFromResource(R.xml.notification_detail)
        findPreference<Preference>("received_at")?.let {
            val cal: Calendar = GregorianCalendar()
            cal.timeInMillis = notification.received
            it.summary = cal.time.toString()
        }

        findPreference<Preference>("message")?.let {
            it.summary = notification.message
        }

        findPreference<Preference>("data")?.let {
            it.summary = notification.data
        }
    }
}
