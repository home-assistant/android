package io.homeassistant.companion.android.settings.developer.location.views

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.database.location.LocationHistoryItem
import io.homeassistant.companion.android.database.location.LocationHistoryItemResult
import io.homeassistant.companion.android.database.location.LocationHistoryItemTrigger
import kotlinx.coroutines.flow.Flow
import java.text.DateFormat
import java.util.TimeZone
import io.homeassistant.companion.android.common.R as commonR

@Composable
fun LocationTrackingView(
    useHistory: Boolean,
    onSetHistory: (Boolean) -> Unit,
    history: Flow<PagingData<LocationHistoryItem>>
) {
    val historyState = history.collectAsLazyPagingItems()

    LazyColumn(
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item("history.use") {
            Row(
                modifier = Modifier
                    .padding(all = 16.dp)
                    .clickable { onSetHistory(!useHistory) }
            ) {
                Text(
                    text = stringResource(commonR.string.location_history_use),
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = useHistory,
                    onCheckedChange = null // Handled by row
                )
            }
        }
        // TODO emptystate
        items(
            count = historyState.itemCount,
            key = historyState.itemKey { "history.${it.id}" }
        ) { index ->
            LocationTrackingHistoryRow(item = historyState[index])
        }
        if (historyState.loadState.append == LoadState.Loading) {
            // TODO
        }
    }
}

@Composable
fun LocationTrackingHistoryRow(item: LocationHistoryItem?) {
    var opened by remember { mutableStateOf(false) }
    val elevation by animateDpAsState(if (opened) 8.dp else 0.dp, label = "historyRowElevation")
    val date by remember(item?.id) {
        mutableStateOf(
            item?.created?.let {
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.DEFAULT).apply {
                    timeZone = TimeZone.getDefault()
                }.format(it)
            }
        )
    }

    Box(Modifier.zIndex(if (opened) 1f else 0f)) {
        Surface(
            shape = RoundedCornerShape(elevation),
            elevation = elevation
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { opened = !opened }
                    .animateContentSize()
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(min = 56.dp)
                        .padding(all = 16.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = date ?: "",
                        style = MaterialTheme.typography.body1
                    )
                    CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            item?.let {
                                val sent = it.result == LocationHistoryItemResult.SENT
                                Text(
                                    text = "${stringResource(item.trigger.toStringResource())} • ${stringResource(it.result.toStringResource())}",
                                    style = MaterialTheme.typography.body2,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                                Image(
                                    asset = if (sent) CommunityMaterial.Icon.cmd_check else CommunityMaterial.Icon.cmd_debug_step_over,
                                    contentDescription = stringResource(
                                        if (sent) commonR.string.location_history_sent else commonR.string.location_history_skipped
                                    ),
                                    colorFilter = ColorFilter.tint(
                                        if (sent) {
                                            colorResource(commonR.color.colorOnAlertSuccess)
                                        } else {
                                            LocalContentColor.current
                                        }
                                    ),
                                    alpha = if (sent) 1.0f else LocalContentAlpha.current,
                                    modifier = Modifier.size(with(LocalDensity.current) { 16.sp.toDp() })
                                )
                            }
                        }
                    }
                }
                if (opened && item != null) {
                    Text("Location ${item.locationName ?: "${item.latitude},${item.longitude}"}")
                    Text("Accuracy ${item.accuracy}")
                    Text("Data ${item.data}")
                    TextButton(onClick = { /*TODO*/ }) {
                        Text("Show on map")
                    }
                    TextButton(onClick = { /*TODO*/ }) {
                        Text("Share")
                    }
                }
            }
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
