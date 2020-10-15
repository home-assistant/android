package io.homeassistant.companion.android.settings.notification

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.notification.NotificationDao
import io.homeassistant.companion.android.database.notification.NotificationItem
import io.homeassistant.companion.android.notifications.DaggerServiceComponent
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

    private lateinit var notificationDao: NotificationDao

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        DaggerServiceComponent
            .builder()
            .appComponent((activity?.application as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)
        notificationDao = AppDatabase.getInstance(requireContext()).notificationDao()

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
