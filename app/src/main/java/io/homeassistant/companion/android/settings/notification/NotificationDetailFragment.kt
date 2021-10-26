package io.homeassistant.companion.android.settings.notification

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.text.Html.fromHtml
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.android.material.composethemeadapter.MdcTheme
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.notification.NotificationItem
import kotlinx.android.parcel.Parcelize
import java.util.Calendar
import java.util.GregorianCalendar

class NotificationDetailFragment(
    private val notification: NotificationItem
) :
    Fragment() {

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

    @Parcelize
    data class NotificationData(val received: Long, val message: String, val data: String) : Parcelable

    @Composable
    private fun LoadNotification(notification: NotificationItem) {
        val notificationItem = rememberSaveable {
            NotificationData(notification.received, notification.message, notification.data)
        }

        Column {
            Text(
                text = getString(R.string.notification_received_at),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                modifier = Modifier
                    .padding(top = 30.dp, bottom = 20.dp, start = 10.dp)
            )
            val cal: Calendar = GregorianCalendar()
            cal.timeInMillis = notificationItem.received
            Text(
                text = cal.time.toString(),
                modifier = Modifier
                    .padding(start = 20.dp)
            )
            Text(
                text = getString(R.string.notification_message),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                modifier = Modifier
                    .padding(top = 20.dp, bottom = 20.dp, start = 10.dp)
            )
            AndroidView(factory = { context ->
                TextView(context).apply {
                    text = fromHtml(notificationItem.message)
                    setPadding(80, 0, 0, 0)
                    textSize = 16f
                }
            })
            Text(
                text = getString(R.string.notification_data),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                modifier = Modifier
                    .padding(top = 20.dp, bottom = 20.dp, start = 10.dp)
            )
            val notifData =
                try {
                    val mapper = ObjectMapper()
                    mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
                    val jsonObject = mapper.readValue(notificationItem.data, Object::class.java)
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObject)
                } catch (e: Exception) {
                    notificationItem.data
                }

            Text(
                text = notifData,
                modifier = Modifier
                    .padding(start = 20.dp)
            )
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
