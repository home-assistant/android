package io.homeassistant.companion.android.settings.notification.views

import android.os.Parcelable
import android.text.Html
import android.widget.TextView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.ObjectMapper
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.database.notification.NotificationItem
import io.homeassistant.companion.android.util.notificationItem
import kotlinx.android.parcel.Parcelize
import java.util.Calendar
import java.util.GregorianCalendar

@Parcelize
data class NotificationData(val received: Long, val message: String, val data: String) : Parcelable

@Composable
fun LoadNotification(notification: NotificationItem) {
    val notificationItem = rememberSaveable {
        NotificationData(notification.received, notification.message, notification.data)
    }

    val scrollState = rememberScrollState()
    Column(modifier = Modifier.verticalScroll(scrollState)) {
        Text(
            text = stringResource(R.string.notification_received_at),
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
            text = stringResource(R.string.notification_message),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 20.sp,
            modifier = Modifier
                .padding(top = 20.dp, bottom = 20.dp, start = 10.dp)
        )
        AndroidView(factory = { context ->
            TextView(context).apply {
                text = Html.fromHtml(notificationItem.message)
                setPadding(80, 0, 0, 0)
                textSize = 16f
            }
        })
        Text(
            text = stringResource(R.string.notification_data),
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

@Preview
@Composable
private fun PreviewNotificationDetails() {
    LoadNotification(notification = notificationItem)
}
