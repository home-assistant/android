package io.homeassistant.companion.android.home.views

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.ToggleButton
import androidx.wear.tooling.preview.devices.WearDevices
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.theme.getToggleButtonColors
import io.homeassistant.companion.android.util.ToggleSwitch
import io.homeassistant.companion.android.util.batterySensorManager
import kotlinx.coroutines.runBlocking

@SuppressLint("InlinedApi")
@Composable
fun SensorUi(
    sensor: Sensor?,
    manager: SensorManager,
    basicSensor: SensorManager.BasicSensor,
    onSensorClicked: (String, Boolean) -> Unit
) {
    val backgroundRequest =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            onSensorClicked(basicSensor.id, it)
        }

    val permissionLaunch = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { isGranted ->
        var allGranted = true
        isGranted.forEach {
            if (
                manager.requiredPermissions(basicSensor.id).contains(Manifest.permission.ACCESS_FINE_LOCATION) &&
                manager.requiredPermissions(basicSensor.id).contains(Manifest.permission.ACCESS_BACKGROUND_LOCATION) &&
                it.key == Manifest.permission.ACCESS_FINE_LOCATION && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            ) {
                backgroundRequest.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                return@forEach
            }
            if (!it.value) {
                allGranted = false
            }
        }
        onSensorClicked(basicSensor.id, allGranted)
    }

    val perm = manager.checkPermission(LocalContext.current, basicSensor.id)
    val isChecked = (sensor == null && basicSensor.enabledByDefault) ||
        (sensor?.enabled == true && perm)
    ToggleButton(
        checked = isChecked,
        onCheckedChange = { enabled ->
            val permissions = manager.requiredPermissions(basicSensor.id)
            if (perm || !enabled) {
                onSensorClicked(basicSensor.id, enabled)
            } else {
                permissionLaunch.launch(
                    if (permissions.size == 1 && permissions[0] == Manifest.permission.ACCESS_BACKGROUND_LOCATION) {
                        permissions
                    } else {
                        permissions.toSet().minus(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            .toTypedArray()
                    }
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth(),
        label = {
            Text(
                text = stringResource(basicSensor.name),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        selectionControl = { ToggleSwitch(isChecked) },
        colors = getToggleButtonColors()
    )
}

@Preview(device = WearDevices.LARGE_ROUND)
@Composable
private fun PreviewSensorUI() {
    val context = LocalContext.current
    CompositionLocalProvider {
        SensorUi(
            sensor = null,
            manager = batterySensorManager,
            basicSensor = runBlocking { batterySensorManager.getAvailableSensors(context).first() }
        ) { _, _ -> }
    }
}
