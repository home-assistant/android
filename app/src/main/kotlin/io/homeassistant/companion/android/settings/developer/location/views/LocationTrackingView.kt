package io.homeassistant.companion.android.settings.developer.location.views

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.ContentAlpha
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.app.ShareCompat
import androidx.core.net.toUri
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.database.location.LocationHistoryItem
import io.homeassistant.companion.android.database.location.LocationHistoryItemResult
import io.homeassistant.companion.android.database.location.LocationHistoryItemTrigger
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.settings.views.EmptyState
import io.homeassistant.companion.android.util.safeBottomPaddingValues
import java.text.DateFormat
import java.util.TimeZone
import kotlinx.coroutines.flow.Flow

@Composable
fun LocationTrackingView(
    useHistory: Boolean,
    onSetHistory: (Boolean) -> Unit,
    history: Flow<PagingData<LocationHistoryItem>>,
    serversList: List<Server>,
) {
    val historyState = history.collectAsLazyPagingItems()

    LazyColumn(
        contentPadding = safeBottomPaddingValues(applyHorizontal = false),
    ) {
        item("history.use") {
            Box(Modifier.padding(all = 16.dp)) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = colorResource(commonR.color.colorSensorTopEnabled),
                ) {
                    Row(
                        modifier = Modifier
                            .clickable { onSetHistory(!useHistory) }
                            .padding(horizontal = 16.dp, vertical = 20.dp),
                    ) {
                        Text(
                            text = stringResource(commonR.string.location_history_use),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = useHistory,
                            // Handled by row
                            onCheckedChange = null,
                            modifier = Modifier.padding(start = 16.dp),
                            colors = SwitchDefaults.colors(
                                uncheckedThumbColor = colorResource(commonR.color.colorSwitchUncheckedThumb),
                            ),
                        )
                    }
                }
            }
        }
        if (!useHistory || (historyState.loadState.refresh !is LoadState.Loading && historyState.itemCount == 0)) {
            item("history.empty") {
                EmptyState(
                    icon = CommunityMaterial.Icon3.cmd_map_marker_path,
                    title = stringResource(
                        if (useHistory) {
                            commonR.string.location_history_empty_title
                        } else {
                            commonR.string.location_history_off_title
                        },
                    ),
                    subtitle = stringResource(
                        if (useHistory) {
                            commonR.string.location_history_empty_summary
                        } else {
                            commonR.string.location_history_off_summary
                        },
                    ),
                )
            }
        } else {
            items(
                count = historyState.itemCount,
                key = historyState.itemKey { "history.${it.id}" },
            ) { index ->
                LocationTrackingHistoryRow(item = historyState[index], servers = serversList)
            }
        }
    }
}

@Composable
fun LocationTrackingHistoryRow(item: LocationHistoryItem?, servers: List<Server>) {
    var opened by rememberSaveable { mutableStateOf(false) }
    val elevation by animateDpAsState(if (opened) 8.dp else 0.dp, label = "HistoryRow elevation")
    val date by remember(item?.id) {
        mutableStateOf(
            item?.created?.let {
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.DEFAULT).apply {
                    timeZone = TimeZone.getDefault()
                }.format(it)
            },
        )
    }

    Box(Modifier.zIndex(if (opened) 1f else 0f)) {
        Surface(
            shape = RoundedCornerShape(elevation),
            color = if (opened) MaterialTheme.colors.surface else MaterialTheme.colors.background,
            elevation = elevation,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { opened = !opened }
                    .animateContentSize(),
            ) {
                ReadOnlyRow(
                    primarySlot = {
                        Text(
                            text = date ?: "",
                            style = MaterialTheme.typography.body1,
                        )
                    },
                    secondarySlot = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            item?.let {
                                val sent = it.result == LocationHistoryItemResult.SENT
                                val failed = it.result == LocationHistoryItemResult.FAILED_SEND
                                Text(
                                    text = "${stringResource(
                                        item.trigger.toStringResource(),
                                    )} • ${stringResource(it.result.toStringResource())}",
                                    style = MaterialTheme.typography.body2,
                                    modifier = Modifier.padding(end = 4.dp),
                                )
                                Image(
                                    asset = when {
                                        sent -> CommunityMaterial.Icon.cmd_check
                                        failed -> CommunityMaterial.Icon.cmd_alert_outline
                                        else -> CommunityMaterial.Icon.cmd_debug_step_over
                                    },
                                    contentDescription = if (sent ||
                                        failed
                                    ) {
                                        null
                                    } else {
                                        stringResource(commonR.string.location_history_skipped)
                                    },
                                    colorFilter = ColorFilter.tint(
                                        when {
                                            sent -> colorResource(commonR.color.colorOnAlertSuccess)
                                            failed -> colorResource(commonR.color.colorOnAlertWarning)
                                            else -> LocalContentColor.current
                                        },
                                    ),
                                    alpha = if (sent || failed) 1.0f else LocalContentAlpha.current,
                                    modifier = Modifier.size(with(LocalDensity.current) { 16.sp.toDp() }),
                                )
                            }
                        }
                    },
                )
                AnimatedVisibility(visible = opened) {
                    val context = LocalContext.current
                    val serverName by remember {
                        mutableStateOf(
                            if (item?.serverId != null) {
                                servers.firstOrNull { it.id == item.serverId }?.friendlyName
                            } else {
                                "-"
                            },
                        )
                    }
                    Column {
                        ReadOnlyRow(
                            primaryText = stringResource(commonR.string.location),
                            secondaryText = (item?.locationName ?: "${item?.latitude}, ${item?.longitude}"),
                        )
                        ReadOnlyRow(
                            primaryText = stringResource(commonR.string.accuracy),
                            secondaryText = item?.accuracy.toString(),
                        )
                        if (servers.size > 1 || serverName == null) { // null serverName suggests deleted server
                            ReadOnlyRow(
                                primaryText = stringResource(commonR.string.server),
                                secondaryText = serverName ?: stringResource(commonR.string.state_unknown),
                            )
                        }
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 8.dp),
                        ) {
                            if (item?.latitude != null && item.longitude != null) {
                                IconButton(
                                    onClick = {
                                        val latlng = "${item.latitude},${item.longitude}"
                                        context.startActivity(
                                            Intent(
                                                Intent.ACTION_VIEW,
                                                "geo:$latlng?q=$latlng(Home+Assistant)".toUri(),
                                            ),
                                        )
                                    },
                                ) {
                                    Image(
                                        asset = CommunityMaterial.Icon3.cmd_map,
                                        contentDescription = stringResource(commonR.string.show_on_map),
                                        modifier = Modifier.size(24.dp),
                                        colorFilter = ColorFilter.tint(MaterialTheme.colors.primary),
                                    )
                                }
                                Spacer(Modifier.width(16.dp))
                            }
                            IconButton(
                                onClick = {
                                    ShareCompat.IntentBuilder(context)
                                        .setText(item?.toSharingString(serverName) ?: "")
                                        .setType("text/plain")
                                        .startChooser()
                                },
                            ) {
                                Image(
                                    asset = CommunityMaterial.Icon3.cmd_share_variant,
                                    contentDescription = stringResource(commonR.string.share_logs),
                                    modifier = Modifier.size(24.dp),
                                    colorFilter = ColorFilter.tint(MaterialTheme.colors.primary),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReadOnlyRow(primaryText: String, secondaryText: String, selectingEnabled: Boolean = true) = ReadOnlyRow(
    { Text(text = primaryText, style = MaterialTheme.typography.body1) },
    {
        if (selectingEnabled) {
            SelectionContainer { Text(text = secondaryText, style = MaterialTheme.typography.body2) }
        } else {
            Text(text = secondaryText, style = MaterialTheme.typography.body2)
        }
    },
)

@Composable
fun ReadOnlyRow(primarySlot: @Composable () -> Unit, secondarySlot: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .heightIn(min = 56.dp)
            .padding(all = 16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        primarySlot()
        CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
            secondarySlot()
        }
    }
}

private fun LocationHistoryItemResult.toStringResource() = when (this) {
    LocationHistoryItemResult.SKIPPED_ACCURACY -> commonR.string.location_history_skipped_accuracy
    LocationHistoryItemResult.SKIPPED_FUTURE -> commonR.string.location_history_skipped_future
    LocationHistoryItemResult.SKIPPED_NOT_LATEST -> commonR.string.location_history_skipped_not_latest
    LocationHistoryItemResult.SKIPPED_DUPLICATE -> commonR.string.location_history_skipped_duplicate
    LocationHistoryItemResult.SKIPPED_DEBOUNCE -> commonR.string.location_history_skipped_debounce
    LocationHistoryItemResult.SKIPPED_OLD -> commonR.string.location_history_skipped_old
    LocationHistoryItemResult.FAILED_SEND -> commonR.string.location_history_failed_send
    LocationHistoryItemResult.SENT -> commonR.string.location_history_sent
}

private fun LocationHistoryItemTrigger.toStringResource() = when (this) {
    LocationHistoryItemTrigger.FLP_BACKGROUND -> commonR.string.basic_sensor_name_location_background
    LocationHistoryItemTrigger.FLP_FOREGROUND -> commonR.string.basic_sensor_name_high_accuracy_mode
    LocationHistoryItemTrigger.GEOFENCE_ENTER -> commonR.string.location_history_geofence_enter
    LocationHistoryItemTrigger.GEOFENCE_EXIT -> commonR.string.location_history_geofence_exit
    LocationHistoryItemTrigger.GEOFENCE_DWELL -> commonR.string.location_history_geofence_dwell
    LocationHistoryItemTrigger.SINGLE_ACCURATE_LOCATION -> commonR.string.basic_sensor_name_location_accurate
    LocationHistoryItemTrigger.UNKNOWN -> commonR.string.state_unknown
}
