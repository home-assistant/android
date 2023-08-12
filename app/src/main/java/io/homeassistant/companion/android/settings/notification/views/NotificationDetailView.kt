package io.homeassistant.companion.android.settings.notification.views

import android.widget.TextView
import androidx.annotation.StringRes
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
import androidx.core.text.HtmlCompat
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
    val valueModifier = Modifier.padding(start = 24.dp)

    Column(modifier = Modifier.verticalScroll(scrollState)) {
        NotificationDetailViewHeader(stringId = commonR.string.notification_received_at)
        val cal: Calendar = GregorianCalendar()
        cal.timeInMillis = notification.received
        Text(
            text = cal.time.toString(),
            modifier = valueModifier
        )

        NotificationDetailViewHeader(stringId = commonR.string.notification_source)
        Text(
            text = notification.source,
            modifier = valueModifier
        )

        NotificationDetailViewHeader(stringId = commonR.string.notification_message)
        AndroidView(
            factory = { context ->
                TextView(context).apply {
                    text = HtmlCompat.fromHtml(notification.message, HtmlCompat.FROM_HTML_MODE_LEGACY)
                    textSize = 16f
                }
            },
            modifier = valueModifier
        )

        NotificationDetailViewHeader(stringId = commonR.string.notification_data)
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
            modifier = valueModifier.then(Modifier.padding(bottom = 16.dp))
        )
    }
}

@Composable
fun NotificationDetailViewHeader(
    @StringRes stringId: Int
) {
    Text(
        text = stringResource(stringId),
        fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp,
        modifier = Modifier
            .padding(top = 32.dp, bottom = 16.dp, start = 16.dp)
    )
}

@Preview
@Composable
private fun PreviewNotificationDetails() {
    LoadNotification(notification = notificationItem)
}
