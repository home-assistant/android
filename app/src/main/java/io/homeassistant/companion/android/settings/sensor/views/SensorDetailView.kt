package io.homeassistant.companion.android.settings.sensor.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.settings.sensor.SensorDetailViewModel
import io.homeassistant.companion.android.common.R as commonR

@Composable
fun SensorDetailView(
    viewModel: SensorDetailViewModel,
    onSetEnabled: (Boolean) -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        if (viewModel.sensorManager != null &&
            viewModel.basicSensor != null
        ) {
            item {
                SensorDetailRow(
                    title = stringResource(commonR.string.enabled),
                    summary = stringResource(commonR.string.enabled_summary),
                    switch = viewModel.sensor.value?.sensor?.enabled
                        ?: (viewModel.sensorManager.enabledByDefault && viewModel.sensorManager.checkPermission(context, viewModel.basicSensor.id)),
                    onClick = { onSetEnabled(it!!) }
                )
            }
            item {
                SensorDetailRow(
                    title = stringResource(commonR.string.sensor_description),
                    summary = stringResource(viewModel.basicSensor.descriptionId)
                )
            }
            viewModel.sensor.value?.let { sensor ->
                item {
                    SensorDetailRow(
                        title = stringResource(commonR.string.unique_id),
                        summary = viewModel.basicSensor.id
                    )
                }
                item {
                    SensorDetailRow(
                        title = stringResource(commonR.string.state),
                        summary = if (!sensor.sensor.enabled) {
                            stringResource(commonR.string.disabled)
                        } else {
                            if (sensor.sensor.unitOfMeasurement.isNullOrBlank()) sensor.sensor.state
                            else "${sensor.sensor.state} ${sensor.sensor.unitOfMeasurement}"
                        }
                    )
                }
                if (sensor.sensor.deviceClass != null) {
                    item {
                        SensorDetailRow(
                            title = stringResource(commonR.string.device_class),
                            summary = sensor.sensor.deviceClass!!
                        )
                    }
                }
                if (sensor.sensor.icon.isNotBlank()) {
                    item {
                        SensorDetailRow(
                            title = stringResource(commonR.string.icon),
                            summary = sensor.sensor.icon
                        )
                    }
                }
                if (sensor.sensor.enabled && sensor.attributes.isNotEmpty()) {
                    item {
                        SensorDetailHeader(stringResource(commonR.string.attributes))
                    }
                    sensor.attributes.forEach { attribute ->
                        item {
                            SensorDetailRow(
                                title = attribute.name,
                                summary = attribute.value
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SensorDetailHeader(text: String) {
    Row(
        modifier = Modifier
            .height(48.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.body2,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.primary
        )
    }
}

@Composable
fun SensorDetailRow(
    title: String,
    summary: String,
    switch: Boolean? = null,
    onClick: (Boolean?) -> Unit = { }
) {
    Row(
        modifier = Modifier
            .clickable { onClick(if (switch != null) !switch else null) }
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.body1
            )
            CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.body2
                )
            }
        }
        if (switch != null) {
            Switch(checked = switch, onCheckedChange = { onClick(!switch) })
        }
    }
}
