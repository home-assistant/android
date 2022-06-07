package io.homeassistant.companion.android.settings.notification

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.composethemeadapter.MdcTheme
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.database.notification.NotificationDao
import io.homeassistant.companion.android.database.notification.NotificationItem
import io.homeassistant.companion.android.settings.notification.views.LoadNotification
import kotlinx.coroutines.launch
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class NotificationDetailFragment : Fragment() {

    companion object {
        val ARG_NOTIF = "notification"
    }

    @Inject
    lateinit var notificationDao: NotificationDao

    private lateinit var notification: NotificationItem

    override fun onCreate(savedInstanceState: Bundle?) {
        notification = arguments?.get(ARG_NOTIF) as NotificationItem
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MdcTheme {
                    LoadNotification(notification)
                }
            }
        }
    }

    private fun deleteConfirmation() {
        val builder: android.app.AlertDialog.Builder = android.app.AlertDialog.Builder(requireContext())

        builder.setTitle(commonR.string.confirm_delete_this_notification_title)
        builder.setMessage(commonR.string.confirm_delete_this_notification_message)

        builder.setPositiveButton(
            commonR.string.confirm_positive
        ) { dialog, _ ->
            lifecycleScope.launch {
                notificationDao.delete(notification.id)
                dialog.dismiss()
                parentFragmentManager.popBackStack()
            }
        }

        builder.setNegativeButton(
            commonR.string.confirm_negative
        ) { dialog, _ -> // Do nothing
            dialog.dismiss()
        }

        val alert: android.app.AlertDialog? = builder.create()
        alert?.show()
    }
}
