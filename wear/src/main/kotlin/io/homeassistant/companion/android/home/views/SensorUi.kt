package io.homeassistant.companion.android.home.views

import android.Manifest
import android.annotation.SuppressLint
import android.health.connect.HealthPermissions
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.tooling.preview.devices.WearDevices
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.theme.getSwitchButtonColors
import io.homeassistant.companion.android.util.batterySensorManager
import io.homeassistant.companion.android.views.ThemeLazyColumn
import kotlinx.coroutines.runBlocking

/**
 * A permission that must be requested in two steps from [minSdk]: [background] can only be
 * requested once [foreground] is granted.
 */
private data class TwoStepPermission(val foreground: String, val background: String, val minSdk: Int)

@SuppressLint("InlinedApi")
private val TWO_STEP_PERMISSIONS = listOf(
    TwoStepPermission(
        foreground = Manifest.permission.ACCESS_FINE_LOCATION,
        background = Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        minSdk = Build.VERSION_CODES.R,
    ),
    TwoStepPermission(
        foreground = Manifest.permission.BODY_SENSORS,
        background = Manifest.permission.BODY_SENSORS_BACKGROUND,
        minSdk = Build.VERSION_CODES.TIRAMISU,
    ),
    TwoStepPermission(
        foreground = HealthPermissions.READ_HEART_RATE,
        background = HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND,
        minSdk = Build.VERSION_CODES.BAKLAVA,
    ),
)

@Composable
fun SensorUi(
    sensor: Sensor?,
    manager: SensorManager,
    basicSensor: SensorManager.BasicSensor,
    onSensorClicked: (String, Boolean) -> Unit,
) {
    var perm by remember { mutableStateOf(false) }
    val backgroundRequest =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            onSensorClicked(basicSensor.id, it)
            perm = it
        }

    val permissionLaunch = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { isGranted ->
        var allGranted = true
        isGranted.forEach { (permission, granted) ->
            val twoStep = TWO_STEP_PERMISSIONS.firstOrNull { it.foreground == permission }
            if (
                twoStep != null &&
                SdkVersion.isAtLeast(twoStep.minSdk) &&
                manager.requiredPermissions(basicSensor.id).contains(twoStep.background)
            ) {
                backgroundRequest.launch(twoStep.background)
                return@forEach
            }
            if (!granted) {
                allGranted = false
            }
        }
        onSensorClicked(basicSensor.id, allGranted)
        perm = allGranted
    }

    LaunchedEffect(Unit) { perm = manager.checkPermission(basicSensor.id) }
    val isChecked = (sensor == null && basicSensor.enabledByDefault) ||
        (sensor?.enabled == true && perm)
    SwitchButton(
        checked = isChecked,
        onCheckedChange = { enabled ->
            val permissions = manager.requiredPermissions(basicSensor.id)
            if (perm || !enabled) {
                onSensorClicked(basicSensor.id, enabled)
            } else {
                val backgroundPermissions = TWO_STEP_PERMISSIONS.map { it.background }
                permissionLaunch.launch(
                    if (permissions.size == 1 && permissions[0] in backgroundPermissions) {
                        permissions
                    } else {
                        permissions.filterNot { it in backgroundPermissions }.toTypedArray()
                    },
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth(),
        label = {
            Text(
                text = stringResource(basicSensor.name),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        secondaryLabel = {
            if (sensor?.enabled == true) {
                sensor.state.let {
                    Text(
                        if (basicSensor.unitOfMeasurement.isNullOrBlank() ||
                            sensor.state.toDoubleOrNull() == null
                        ) {
                            it
                        } else {
                            "$it ${sensor.unitOfMeasurement}"
                        },
                    )
                }
            }
        },
        colors = getSwitchButtonColors(),
    )
}

@Preview(device = WearDevices.LARGE_ROUND)
@Composable
private fun PreviewSensorUI() {
    val context = LocalContext.current
    val batterySensorManager = batterySensorManager(context)
    val batterySensors = runBlocking { batterySensorManager.getAvailableSensors() }
    CompositionLocalProvider {
        ThemeLazyColumn {
            item {
                SensorUi(
                    sensor = Sensor(
                        "battery_level",
                        0,
                        true,
                        state = "80",
                        unitOfMeasurement = "%",
                    ),
                    manager = batterySensorManager,
                    basicSensor = batterySensors.first { it.id == "battery_level" },
                ) { _, _ -> }
            }

            item {
                SensorUi(
                    sensor = Sensor(
                        "is_charging",
                        0,
                        true,
                        state = "true",
                    ),
                    manager = batterySensorManager,
                    basicSensor = batterySensors.first { it.id == "is_charging" },
                ) { _, _ -> }
            }

            item {
                SensorUi(
                    sensor = null,
                    manager = batterySensorManager,
                    basicSensor = batterySensors.first { it.id == "battery_power" },
                ) { _, _ -> }
            }
        }
    }
}
