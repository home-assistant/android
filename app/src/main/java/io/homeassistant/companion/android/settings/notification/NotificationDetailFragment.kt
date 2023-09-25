package io.homeassistant.companion.android.settings.notification

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.MenuHost
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.google.accompanist.themeadapter.material.MdcTheme
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
        const val ARG_NOTIF = "notification"
    }

    @Inject
    lateinit var notificationDao: NotificationDao

    private lateinit var notification: NotificationItem

    override fun onCreate(savedInstanceState: Bundle?) {
        notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arguments?.getSerializable(ARG_NOTIF, NotificationItem::class.java)
        } else {
            @Suppress("DEPRECATION")
            arguments?.get(ARG_NOTIF) as? NotificationItem
        } ?: return
        super.onCreate(savedInstanceState)
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
            Lifecycle.State.RESUMED
        )
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
