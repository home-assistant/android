package io.homeassistant.companion.android.settings.sensor.views

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Divider
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.compose.Image
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.sensors.id
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.settings.sensor.SensorSettingsViewModel
import io.homeassistant.companion.android.common.R as commonR

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SensorListView(
    viewModel: SensorSettingsViewModel,
    managers: List<SensorManager>,
    onSensorClicked: (String) -> Unit
) {
    val context = LocalContext.current
    val listEntries = managers.associateWith { manager ->
        manager.getAvailableSensors(context)
            .filter { basicSensor ->
                viewModel.sensors.any { basicSensor.id == it.id }
            }
            .sortedBy { context.getString(it.name) }
    }

    LazyColumn {
        listEntries.forEach { (manager, currentSensors) ->
            stickyHeader(
                key = manager.id()
            ) {
                if (currentSensors.any()) {
                    Column(
                        modifier = Modifier
                            .background(MaterialTheme.colors.background)
                            .fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .height(48.dp)
                                .padding(start = 72.dp, end = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(manager.name),
                                style = MaterialTheme.typography.body2,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colors.primary
                            )
                        }
                    }
                }
            }
            items(
                items = currentSensors,
                key = { "${manager.id()}_${it.id}" }
            ) { basicSensor ->
                SensorRow(
                    basicSensor = basicSensor,
                    dbSensor = viewModel.sensors.firstOrNull { it.id == basicSensor.id },
                    onSensorClicked = onSensorClicked
                )
            }
            if (currentSensors.any() && manager.id() != listEntries.keys.last().id()) {
                item {
                    Divider()
                }
            }
        }
    }
}

@Composable
fun SensorRow(
    basicSensor: SensorManager.BasicSensor,
    dbSensor: Sensor?,
    onSensorClicked: (String) -> Unit
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .clickable { onSensorClicked(basicSensor.id) }
            .heightIn(min = 72.dp)
            .padding(all = 16.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        var iconToUse = basicSensor.statelessIcon
        if (dbSensor?.enabled == true && dbSensor.icon.isNotBlank()) {
            iconToUse = dbSensor.icon
        }
        val mdiIcon = try {
            IconicsDrawable(context, "cmd-${iconToUse.split(":")[1]}").icon
        } catch (e: Exception) { null }

        if (mdiIcon != null) {
            Image(
                asset = mdiIcon,
                modifier = Modifier
                    .size(24.dp)
                    .alpha(if (dbSensor?.enabled == true) ContentAlpha.high else ContentAlpha.disabled),
                colorFilter = ColorFilter.tint(
                    if (dbSensor?.enabled == true) colorResource(commonR.color.colorSensorIconEnabled)
                    else contentColorFor(backgroundColor = MaterialTheme.colors.background)
                )
            )
        } else {
            Spacer(modifier = Modifier.width(24.dp))
        }
        Spacer(modifier = Modifier.width(32.dp))
        Column(
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(basicSensor.name),
                style = MaterialTheme.typography.body1
            )
            CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                Text(
                    text = if (dbSensor?.enabled == true) {
                        if (dbSensor.state.isBlank()) {
                            stringResource(commonR.string.enabled)
                        } else {
                            if (basicSensor.unitOfMeasurement.isNullOrBlank()) dbSensor.state
                            else "${dbSensor.state} ${basicSensor.unitOfMeasurement}"
                        }
                    } else {
                        stringResource(commonR.string.disabled)
                    },
                    style = MaterialTheme.typography.body2
                )
            }
        }
    }
}
