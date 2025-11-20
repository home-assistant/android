package io.homeassistant.companion.android.settings.notification

import android.app.AlertDialog
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.core.text.HtmlCompat
import androidx.core.view.MenuHost
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.database.notification.NotificationDao
import io.homeassistant.companion.android.database.notification.NotificationItem
import io.homeassistant.companion.android.util.applyBottomSafeDrawingInsets
import java.util.Calendar
import java.util.GregorianCalendar
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NotificationHistoryFragment : PreferenceFragmentCompat() {

    private var filterValue = 25

    @Inject
    lateinit var notificationDao: NotificationDao

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(
            object : NotificationMenuProvider() {
                override fun onPrepareMenu(menu: Menu) {
                    super.onPrepareMenu(menu)
                    menu.findItem(R.id.last25)?.title = getString(commonR.string.last_num_notifications, 25)
                    menu.findItem(R.id.last50)?.title = getString(commonR.string.last_num_notifications, 50)
                    menu.findItem(R.id.last100)?.title = getString(commonR.string.last_num_notifications, 100)

                    val prefCategory = findPreference<PreferenceCategory>("list_notifications")
                    lifecycleScope.launch {
                        val allNotifications = notificationDao.getAll()

                        if (allNotifications.isEmpty()) {
                            menu.removeItem(R.id.search_notifications)
                            menu.removeItem(R.id.notification_filter)
                            menu.removeItem(R.id.action_delete)
                        } else {
                            val searchViewItem = menu.findItem(R.id.search_notifications)
                            val searchView = searchViewItem?.actionView as? SearchView
                            searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                                override fun onQueryTextSubmit(query: String?): Boolean {
                                    searchView.clearFocus()

                                    return false
                                }

                                override fun onQueryTextChange(query: String?): Boolean {
                                    var searchList: Array<NotificationItem> = emptyArray()
                                    if (!query.isNullOrEmpty()) {
                                        for (item in allNotifications) {
                                            if (item.message.contains(query, true)) {
                                                searchList += item
                                            }
                                        }
                                        prefCategory?.title = getString(commonR.string.search_results)
                                        reloadNotifications(searchList, prefCategory)
                                    } else if (query.isNullOrEmpty()) {
                                        prefCategory?.title = getString(commonR.string.notifications)
                                        filterNotifications(filterValue, notificationDao, prefCategory)
                                    }
                                    return false
                                }
                            })
                        }
                    }
                }

                override fun onMenuItemSelected(menuItem: MenuItem) = when (menuItem.itemId) {
                    R.id.last25, R.id.last50, R.id.last100 -> {
                        val prefCategory = findPreference<PreferenceCategory>("list_notifications")
                        filterValue = when (menuItem.itemId) {
                            R.id.last25 -> 25
                            R.id.last50 -> 50
                            R.id.last100 -> 100
                            else -> 25
                        }
                        menuItem.isChecked = !menuItem.isChecked
                        filterNotifications(filterValue, notificationDao, prefCategory)
                        true
                    }
                    R.id.action_delete -> {
                        deleteAllConfirmation(notificationDao)
                        true
                    }
                    else -> false
                }
            },
            viewLifecycleOwner,
            Lifecycle.State.RESUMED,
        )
        applyBottomSafeDrawingInsets()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // This cause StrictMode to trigger since it loads the XML from the disk
        // we could put this in IO thread but with the risk that following
        // findPreference return null and cause the UI to be wrong.
        setPreferencesFromResource(R.xml.notifications, rootKey)
    }

    override fun onResume() {
        super.onResume()

        activity?.title = getString(commonR.string.notifications)
        lifecycleScope.launch {
            val notificationList = notificationDao.getLastItems(25)

            val prefCategory = findPreference<PreferenceCategory>("list_notifications")
            if (notificationList.isNotEmpty()) {
                prefCategory?.isVisible = true
                reloadNotifications(notificationList, prefCategory)
            } else {
                findPreference<PreferenceCategory>("list_notifications")?.let {
                    it.isVisible = false
                }
                findPreference<Preference>("no_notifications")?.let {
                    it.isVisible = true
                }
            }
        }
    }

    private fun deleteAllConfirmation(notificationDao: NotificationDao) {
        val builder: AlertDialog.Builder = AlertDialog.Builder(requireContext())

        builder.setTitle(commonR.string.confirm_delete_all_notification_title)
        builder.setMessage(commonR.string.confirm_delete_all_notification_message)

        builder.setPositiveButton(
            commonR.string.confirm_positive,
        ) { dialog, _ ->
            lifecycleScope.launch {
                notificationDao.deleteAll()
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

        val alert: AlertDialog? = builder.create()
        alert?.show()
    }

    private fun reloadNotifications(notificationList: Array<NotificationItem>, prefCategory: PreferenceCategory?) {
        prefCategory?.removeAll()
        for (item in notificationList) {
            val pref = Preference(preferenceScreen.context)
            val cal: Calendar = GregorianCalendar()
            cal.timeInMillis = item.received
            pref.key = item.id.toString()
            pref.title = "${cal.time} - ${item.source}"
            pref.summary = HtmlCompat.fromHtml(item.message, HtmlCompat.FROM_HTML_MODE_LEGACY)
            pref.isIconSpaceReserved = false

            pref.setOnPreferenceClickListener {
                val args = Bundle()
                args.putSerializable(NotificationDetailFragment.ARG_NOTIF, item)
                parentFragmentManager.commit {
                    replace(R.id.content, NotificationDetailFragment::class.java, args)
                    addToBackStack("Notification Detail")
                }
                return@setOnPreferenceClickListener true
            }

            prefCategory?.addPreference(pref)
        }
    }

    private fun filterNotifications(
        filterValue: Int,
        notificationDao: NotificationDao,
        prefCategory: PreferenceCategory?,
    ) {
        lifecycleScope.launch {
            val notificationList = notificationDao.getLastItems(filterValue)
            reloadNotifications(notificationList, prefCategory)
        }
    }
}
