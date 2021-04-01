package io.homeassistant.companion.android.settings.notification

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html.fromHtml
import android.view.Menu
import android.view.MenuItem
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.setGroupVisible(R.id.notification_toolbar_group, true)
        menu.removeItem(R.id.search_notifications)
        menu.removeItem(R.id.notification_filter)

        menu.findItem(R.id.get_help)?.let {
            it.isVisible = true
            it.intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://companion.home-assistant.io/docs/notifications/notifications-basic"))
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_delete)
            deleteConfirmation()
        return super.onOptionsItemSelected(item)
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
