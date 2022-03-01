package io.homeassistant.companion.android.settings.notification.views

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.IconicsDrawable
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.settings.notification.NotificationViewModel

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun NotificationChannelView(
    notificationViewModel: NotificationViewModel
) {
    val context = LocalContext.current
    LazyColumn(contentPadding = PaddingValues(20.dp)) {
        item {
            Text(
                text = stringResource(id = R.string.notification_channels_description),
                modifier = Modifier.padding(bottom = 20.dp)
            )
            Divider()
        }

        items(notificationViewModel.channelList[0].size) { index ->
            val channelId = notificationViewModel.channelList[0][index].id
            Row(
                modifier = Modifier
                    .padding(10.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = notificationViewModel.channelList[0][index].name.toString().take(30)
                )
                val editIcon =
                    IconicsDrawable(context, "cmd-pencil").toBitmap().asImageBitmap()
                val deleteIcon =
                    IconicsDrawable(context, "cmd-delete").toBitmap().asImageBitmap()
                Row {
                    Icon(
                        editIcon,
                        "",
                        modifier = Modifier
                            .clickable { notificationViewModel.editChannelDetails(channelId) }
                            .padding(end = 10.dp)
                    )
                    Icon(
                        deleteIcon,
                        "",
                        modifier = Modifier
                            .clickable {
                                notificationViewModel.deleteChannel(channelId)
                                notificationViewModel.updateChannelList()
                            }
                            .padding(start = 10.dp)
                    )
                }
            }
            Divider()
        }
    }
}
