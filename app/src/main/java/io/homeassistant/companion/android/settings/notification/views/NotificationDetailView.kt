package io.homeassistant.companion.android.settings.notification.views

import android.text.Html
import android.widget.TextView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.ObjectMapper
import io.homeassistant.companion.android.database.notification.NotificationItem
import io.homeassistant.companion.android.util.notificationItem
import java.util.Calendar
import java.util.GregorianCalendar
import io.homeassistant.companion.android.common.R as commonR

@Composable
fun LoadNotification(notification: NotificationItem) {

    val scrollState = rememberScrollState()
    Column(modifier = Modifier.verticalScroll(scrollState)) {
        Text(
            text = stringResource(commonR.string.notification_received_at),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 20.sp,
            modifier = Modifier
                .padding(top = 30.dp, bottom = 20.dp, start = 10.dp)
        )
        val cal: Calendar = GregorianCalendar()
        cal.timeInMillis = notification.received
        Text(
            text = cal.time.toString(),
            modifier = Modifier
                .padding(start = 20.dp)
        )
        Text(
            text = stringResource(commonR.string.notification_message),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 20.sp,
            modifier = Modifier
                .padding(top = 20.dp, bottom = 20.dp, start = 10.dp)
        )
        AndroidView(factory = { context ->
            TextView(context).apply {
                text = Html.fromHtml(notification.message)
                setPadding(80, 0, 0, 0)
                textSize = 16f
            }
        })
        Text(
            text = stringResource(commonR.string.notification_data),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 20.sp,
            modifier = Modifier
                .padding(top = 20.dp, bottom = 20.dp, start = 10.dp)
        )
        val notifData =
            try {
                val mapper = ObjectMapper()
                mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
                val jsonObject = mapper.readValue(notification.data, Object::class.java)
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObject)
            } catch (e: Exception) {
                notification.data
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
