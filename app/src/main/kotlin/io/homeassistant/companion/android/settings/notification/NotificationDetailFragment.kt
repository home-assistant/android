package io.homeassistant.companion.android.settings.notification

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.MenuHost
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.database.notification.NotificationDao
import io.homeassistant.companion.android.database.notification.NotificationItem
import io.homeassistant.companion.android.settings.notification.views.LoadNotification
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class NotificationDetailFragment : Fragment() {

    companion object {
        private const val ARG_NOTIF_ID = "notification_id"

        /** Arguments for displaying the notification stored under [notificationId]. */
        fun newArgs(notificationId: Int): Bundle = Bundle().apply {
            putInt(ARG_NOTIF_ID, notificationId)
        }
    }

    @Inject
    lateinit var notificationDao: NotificationDao

    private val notificationId: Int
        get() = requireArguments().getInt(ARG_NOTIF_ID)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val notification by produceState<NotificationItem?>(initialValue = null) {
                    value = notificationDao.get(notificationId)
                    if (value == null) {
                        // The row was removed while the detail screen was open, there is nothing to show.
                        Timber.w("No notification found for id $notificationId, leaving the detail screen")
                        parentFragmentManager.popBackStack()
                    }
                }
                HomeAssistantAppTheme {
                    notification?.let { LoadNotification(it) }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(
            object : NotificationMenuProvider() {
                override fun onPrepareMenu(menu: Menu) {
                    super.onPrepareMenu(menu)
                    menu.removeItem(R.id.search_notifications)
                    menu.removeItem(R.id.notification_filter)
                }

                override fun onMenuItemSelected(menuItem: MenuItem) = when (menuItem.itemId) {
                    R.id.action_delete -> {
                        deleteConfirmation()
                        true
                    }
                    else -> false
                }
            },
            viewLifecycleOwner,
            Lifecycle.State.RESUMED,
        )
    }

    private fun deleteConfirmation() {
        val builder: android.app.AlertDialog.Builder = android.app.AlertDialog.Builder(requireContext())

        builder.setTitle(commonR.string.confirm_delete_this_notification_title)
        builder.setMessage(commonR.string.confirm_delete_this_notification_message)

        builder.setPositiveButton(
            commonR.string.confirm_positive,
        ) { dialog, _ ->
            lifecycleScope.launch {
                notificationDao.delete(notificationId)
                dialog.dismiss()
                parentFragmentManager.popBackStack()
            }
        }

        builder.setNegativeButton(
            commonR.string.confirm_negative,
        ) { dialog, _ ->
            // Do nothing
            dialog.dismiss()
        }

        val alert: android.app.AlertDialog? = builder.create()
        alert?.show()
    }
}
