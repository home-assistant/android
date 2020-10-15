package io.homeassistant.companion.android.settings.notification

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.database.AppDatabase
import java.util.Calendar
import java.util.GregorianCalendar

class NotificationHistoryFragment : PreferenceFragmentCompat() {

    companion object {
        fun newInstance(): NotificationHistoryFragment {
            return NotificationHistoryFragment()
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

        setPreferencesFromResource(R.xml.notifications, rootKey)
        val notificationDao = AppDatabase.getInstance(requireContext()).notificationDao()
        val notificationList = notificationDao.getLast25()

        val prefCategory = PreferenceCategory(preferenceScreen.context)
        if (!notificationList.isNullOrEmpty()) {
            prefCategory.title = requireContext().getString(R.string.last_25_notifications)
            prefCategory.isIconSpaceReserved = false
            preferenceScreen.addPreference(prefCategory)
            for (item in notificationList) {
                val pref = Preference(preferenceScreen.context)
                val cal: Calendar = GregorianCalendar()
                cal.timeInMillis = item.received
                pref.key = item.id.toString()
                pref.title = cal.time.toString()
                pref.summary = item.message
                pref.isIconSpaceReserved = false

                pref.setOnPreferenceClickListener {
                    parentFragmentManager
                        .beginTransaction()
                        .replace(
                            R.id.content,
                            NotificationDetailFragment.newInstance(
                                item
                            )
                        )
                        .addToBackStack("Notification Detail")
                        .commit()
                    return@setOnPreferenceClickListener true
                }

                prefCategory.addPreference(pref)
            }
        } else {
            findPreference<Preference>("no_notifications")?.let {
                it.isVisible = true
            }
        }
    }
}
