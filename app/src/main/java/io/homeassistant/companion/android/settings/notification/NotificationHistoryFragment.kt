package io.homeassistant.companion.android.settings.notification

import android.app.AlertDialog
import android.os.Bundle
import android.text.Html.fromHtml
import androidx.preference.DropDownPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.notification.NotificationDao
import io.homeassistant.companion.android.database.notification.NotificationItem
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
    }

    override fun onResume() {
        super.onResume()

        val notificationDao = AppDatabase.getInstance(requireContext()).notificationDao()
        val notificationList = notificationDao.getLastItems(25)

        val prefCategory = findPreference<PreferenceCategory>("list_notifications")
        if (!notificationList.isNullOrEmpty()) {
            prefCategory?.isVisible = true
            prefCategory?.title = getString(R.string.last_num_notifications, 25)
            reloadNotifications(notificationList, prefCategory)

            findPreference<PreferenceCategory>("manage_notifications")?.let {
                it.isVisible = true
            }

            findPreference<DropDownPreference>("filter_notifications")?.let {
                it.isVisible = true
                it.setOnPreferenceChangeListener { _, newValue ->
                    when (newValue) {
                        "last25" -> {
                            prefCategory?.title = getString(R.string.last_num_notifications, 25)
                            reloadNotifications(notificationList, prefCategory)
                        }
                        "last50" -> {
                            val newList = notificationDao.getLastItems(50)
                            prefCategory?.title = getString(R.string.last_num_notifications, 50)
                            reloadNotifications(newList, prefCategory)
                        }
                        "last100" -> {
                            val newList = notificationDao.getLastItems(100)
                            prefCategory?.title = getString(R.string.last_num_notifications, 100)
                            reloadNotifications(newList, prefCategory)
                        }
                        else -> {
                            prefCategory?.title = getString(R.string.last_num_notifications, 25)
                            reloadNotifications(notificationList, prefCategory)
                        }
                    }
                    return@setOnPreferenceChangeListener true
                }
            }

            findPreference<Preference>("delete_all")?.let {
                it.isVisible = true
                it.setOnPreferenceClickListener {
                    deleteAllConfirmation(notificationDao)
                    return@setOnPreferenceClickListener true
                }
            }
        } else {
            findPreference<PreferenceCategory>("manage_notifications")?.let {
                it.isVisible = false
            }
            findPreference<PreferenceCategory>("list_notifications")?.let {
                it.isVisible = false
            }
            findPreference<Preference>("no_notifications")?.let {
                it.isVisible = true
            }
        }
    }

    private fun deleteAllConfirmation(notificationDao: NotificationDao) {
        val builder: AlertDialog.Builder = AlertDialog.Builder(requireContext())

        builder.setTitle(R.string.confirm_delete_all_notification_title)
        builder.setMessage(R.string.confirm_delete_all_notification_message)

        builder.setPositiveButton(
            R.string.confirm_positive
        ) { dialog, _ ->
            notificationDao.deleteAll()
            dialog.dismiss()
            parentFragmentManager.popBackStack()
        }

        builder.setNegativeButton(
            R.string.confirm_negative
        ) { dialog, _ -> // Do nothing
            dialog.dismiss()
        }

        val alert: AlertDialog? = builder.create()
        alert?.show()
    }

    private fun reloadNotifications(notificationList: Array<NotificationItem>?, prefCategory: PreferenceCategory?) {
        prefCategory?.removeAll()
        if (notificationList != null) {
            for (item in notificationList) {
                val pref = Preference(preferenceScreen.context)
                val cal: Calendar = GregorianCalendar()
                cal.timeInMillis = item.received
                pref.key = item.id.toString()
                pref.title = cal.time.toString()
                pref.summary = fromHtml(item.message)
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

                prefCategory?.addPreference(pref)
            }
        }
    }
}
