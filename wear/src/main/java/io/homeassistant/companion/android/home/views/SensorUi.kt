package io.homeassistant.companion.android.home.views

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.database.sensor.SensorDao

@Composable
fun SensorUi(
    sensorDao: SensorDao,
    manager: SensorManager,
    basicSensor: SensorManager.BasicSensor,
    onSensorClicked: (String, Boolean) -> Unit,
//    isHapticEnabled: Boolean,
//    isToastEnabled: Boolean
) {
    val dao = sensorDao.get(basicSensor.id)
    val perm = manager.checkPermission(LocalContext.current, basicSensor.id)
    val checked = manager.isEnabled(LocalContext.current, basicSensor.id)
    ToggleChip(
        checked = (dao == null && manager.enabledByDefault) ||
            (dao?.enabled == true && perm),
        onCheckedChange = { enabled ->
            onSensorClicked(basicSensor.id, enabled)
//                onEntityClickedFeedback(isToastEnabled, isHapticEnabled, context, friendlyName, haptic)
        },
        modifier = Modifier
            .fillMaxWidth(),
        /*appIcon = {
            Image(
                asset = iconBitmap ?: CommunityMaterial.Icon.cmd_cellphone,
                colorFilter = ColorFilter.tint(wearColorPalette.onSurface)
            )
        },*/
        label = {
            Text(
                text = stringResource(basicSensor.name),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        toggleIcon = { ToggleChipDefaults.SwitchIcon(checked) }
    )
}
