package io.homeassistant.companion.android.settings.notification

import android.os.Bundle
import android.text.Html.fromHtml
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.database.AppDatabase
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
            it.summary = fromHtml(notification.message)
        }

        findPreference<Preference>("data")?.let {
            it.summary = notification.data
        }

        findPreference<Preference>("delete_notification")?.let {
            it.setOnPreferenceClickListener {

                deleteConfirmation()

                return@setOnPreferenceClickListener true
            }
        }
    }

    private fun deleteConfirmation() {
        val notificationDao = AppDatabase.getInstance(requireContext()).notificationDao()

        val builder: android.app.AlertDialog.Builder = android.app.AlertDialog.Builder(requireContext())

        builder.setTitle(R.string.confirm_delete_this_notification_title)
        builder.setMessage(R.string.confirm_delete_this_notification_message)

        builder.setPositiveButton(
            R.string.confirm_positive
        ) { dialog, _ ->
            notificationDao.delete(notification.id)
            dialog.dismiss()
            parentFragmentManager.popBackStack()
        }

        builder.setNegativeButton(
            R.string.confirm_negative
        ) { dialog, _ -> // Do nothing
            dialog.dismiss()
        }

        val alert: android.app.AlertDialog? = builder.create()
        alert?.show()
    }
}
