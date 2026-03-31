package io.homeassistant.companion.android.settings.notification

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.core.os.BundleCompat
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

@AndroidEntryPoint
class NotificationDetailFragment : Fragment() {

    companion object {
        const val ARG_NOTIF = "notification"
    }

    @Inject
    lateinit var notificationDao: NotificationDao

    private lateinit var notification: NotificationItem

    override fun onCreate(savedInstanceState: Bundle?) {
        notification = arguments?.let {
            BundleCompat.getSerializable(it, ARG_NOTIF, NotificationItem::class.java)
        } ?: return
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                HomeAssistantAppTheme {
                    LoadNotification(notification)
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
                notificationDao.delete(notification.id)
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
