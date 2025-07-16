package io.homeassistant.companion.android.settings.notification.views

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarResult
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.util.appCreatedChannels
import io.homeassistant.companion.android.settings.notification.NotificationViewModel
import io.homeassistant.companion.android.util.plus
import io.homeassistant.companion.android.util.safeBottomPaddingValues
import io.homeassistant.companion.android.util.safeBottomWindowInsets
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun NotificationChannelView(notificationViewModel: NotificationViewModel) {
    val scaffoldState = rememberScaffoldState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    Scaffold(
        scaffoldState = scaffoldState,
        snackbarHost = {
            SnackbarHost(
                hostState = scaffoldState.snackbarHostState,
                modifier = Modifier.windowInsetsPadding(safeBottomWindowInsets(applyHorizontal = false)),
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.padding(contentPadding),
            contentPadding = PaddingValues(all = 16.dp) + safeBottomPaddingValues(applyHorizontal = false),
        ) {
            item {
                Text(
                    text = stringResource(id = R.string.notification_channels_description),
                    modifier = Modifier.padding(bottom = 20.dp),
                )
                Divider()
            }

            items(notificationViewModel.channelList.size) { index ->
                val channel = notificationViewModel.channelList[index]
                Row(
                    modifier = Modifier
                        .padding(10.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = channel.name.toString().take(30),
                    )
                    Row(
                        modifier = Modifier.padding(start = 12.dp),
                    ) {
                        Icon(
                            Icons.Filled.Edit,
                            stringResource(id = R.string.edit_channel),
                            modifier = Modifier
                                .clickable { notificationViewModel.editChannelDetails(channel.id) }
                                .padding(12.dp),
                        )
                        if (channel.id !in appCreatedChannels) {
                            Icon(
                                Icons.Filled.Delete,
                                stringResource(id = R.string.delete_channel),
                                modifier = Modifier
                                    .clickable {
                                        notificationViewModel.deleteChannel(channel.id)
                                        notificationViewModel.updateChannelList()
                                        scope.launch {
                                            scaffoldState.snackbarHostState
                                                .showSnackbar(
                                                    context.getString(
                                                        R.string.notification_channel_deleted,
                                                        channel.name,
                                                    ),
                                                    context.getString(R.string.undo),
                                                )
                                                .let {
                                                    if (it == SnackbarResult.ActionPerformed) {
                                                        notificationViewModel.createChannel(channel)
                                                        notificationViewModel.updateChannelList()
                                                    }
                                                }
                                        }
                                    }
                                    .padding(12.dp),
                            )
                        }
                    }
                }
                Divider()
            }
        }
    }
}
