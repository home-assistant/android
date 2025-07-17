package io.homeassistant.companion.android.settings.sensor.views

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mikepenz.iconics.IconicsDrawable
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.sensors.id
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.settings.sensor.SensorSettingsViewModel
import io.homeassistant.companion.android.settings.views.SettingsRow
import io.homeassistant.companion.android.settings.views.SettingsSubheader
import io.homeassistant.companion.android.settings.views.SettingsSubheaderDefaults
import io.homeassistant.companion.android.util.safeBottomPaddingValues

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SensorListView(viewModel: SensorSettingsViewModel, onSensorClicked: (String) -> Unit) {
    LazyColumn(
        contentPadding = safeBottomPaddingValues(applyHorizontal = false),
    ) {
        viewModel.allSensors.filter { it.value.isNotEmpty() }.forEach { (manager, currentSensors) ->
            stickyHeader(
                key = manager.id(),
            ) {
                if (currentSensors.any()) {
                    SettingsSubheader(
                        text = stringResource(manager.name),
                        modifier = Modifier
                            .background(MaterialTheme.colors.background)
                            .fillMaxWidth(),
                        textPadding = SettingsSubheaderDefaults.TextWithIconRowPadding,
                    )
                }
            }
            items(
                items = currentSensors,
                key = { "${manager.id()}_${it.id}" },
            ) { basicSensor ->
                SensorRow(
                    basicSensor = basicSensor,
                    dbSensor = viewModel.sensors[basicSensor.id],
                    onSensorClicked = onSensorClicked,
                )
            }
            if (currentSensors.any() && manager.id() != viewModel.allSensors.keys.last().id()) {
                item {
                    Divider()
                }
            }
        }
    }
}

@Composable
fun SensorRow(basicSensor: SensorManager.BasicSensor, dbSensor: Sensor?, onSensorClicked: (String) -> Unit) {
    val context = LocalContext.current
    var iconToUse = basicSensor.statelessIcon
    if (dbSensor?.enabled == true && dbSensor.icon.isNotBlank()) {
        iconToUse = dbSensor.icon
    }
    val mdiIcon = try {
        IconicsDrawable(context, "cmd-${iconToUse.split(":")[1]}").icon
    } catch (e: Exception) {
        null
    }

    SettingsRow(
        primaryText = stringResource(basicSensor.name),
        secondaryText = if (dbSensor?.enabled == true) {
            if (dbSensor.state.isBlank()) {
                stringResource(commonR.string.enabled)
            } else {
                if (basicSensor.unitOfMeasurement.isNullOrBlank() || dbSensor.state.toDoubleOrNull() == null) {
                    dbSensor.state
                } else {
                    "${dbSensor.state} ${basicSensor.unitOfMeasurement}"
                }
            }
        } else {
            stringResource(commonR.string.disabled)
        },
        mdiIcon = mdiIcon,
        enabled = dbSensor?.enabled == true,
    ) { onSensorClicked(basicSensor.id) }
}
