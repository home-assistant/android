package io.homeassistant.companion.android.home.views

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.database.sensor.Sensor

@Composable
fun SensorUi(
    sensor: Sensor?,
    manager: SensorManager,
    basicSensor: SensorManager.BasicSensor,
    onSensorClicked: (String, Boolean) -> Unit,
) {
    var checked = sensor?.enabled == true

    val backgroundRequest =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    val permissionLaunch = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { isGranted ->
        var allGranted = true
        isGranted.forEach {
            if (manager.requiredPermissions(basicSensor.id)
                .contains(Manifest.permission.ACCESS_FINE_LOCATION) && manager.requiredPermissions(basicSensor.id)
                    .contains(Manifest.permission.ACCESS_BACKGROUND_LOCATION) && it.key == Manifest.permission.ACCESS_FINE_LOCATION
            )
                backgroundRequest.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            if (!it.value)
                allGranted = false
        }
        checked = sensor?.enabled == true && allGranted
    }

    val perm = manager.checkPermission(LocalContext.current, basicSensor.id)
    ToggleChip(
        checked = (sensor == null && manager.enabledByDefault) ||
            (sensor?.enabled == true && perm),
        onCheckedChange = { enabled ->
            val permissions = manager.requiredPermissions(basicSensor.id)
            if (perm)
                onSensorClicked(basicSensor.id, enabled)
            else
                permissionLaunch.launch(
                    if (permissions.size == 1 && permissions[0] == Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        permissions
                    else
                        permissions.toSet().minus(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            .toTypedArray()
                )
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
        toggleControl = {
            Icon(
                imageVector = ToggleChipDefaults.switchIcon(checked),
                contentDescription = if (checked)
                    stringResource(R.string.enabled)
                else
                    stringResource(R.string.disabled)
            )
        }
    )
}
