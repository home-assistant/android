package io.homeassistant.companion.android.home.views

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.ExperimentalWearMaterialApi
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.ScalingLazyListState
import androidx.wear.compose.material.rememberScalingLazyListState
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.theme.WearAppTheme
//import io.homeassistant.companion.android.util.previewSensorManager

@ExperimentalAnimationApi
@ExperimentalWearMaterialApi
@Composable
@ExperimentalComposeUiApi
fun SensorManagerUi(
    allSensors: List<Sensor>?,
    sensorManager: SensorManager,
    onSensorClicked: (String, Boolean) -> Unit,
//    isHapticEnabled: Boolean,
//    isToastEnabled: Boolean
) {
    val scalingLazyListState: ScalingLazyListState = rememberScalingLazyListState()

    WearAppTheme {
        Scaffold(
            positionIndicator = {
                if (scalingLazyListState.isScrollInProgress)
                    PositionIndicator(scalingLazyListState = scalingLazyListState)
            },
            timeText = { TimeText(!scalingLazyListState.isScrollInProgress) }
        ) {
            val availableSensors = sensorManager.getAvailableSensors(LocalContext.current)
            ScalingLazyColumn(
                modifier = Modifier
                    .fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 24.dp,
                    start = 8.dp,
                    end = 8.dp,
                    bottom = 48.dp
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                state = scalingLazyListState
            ) {
                item {
                    ListHeader(id = sensorManager.name)
                }
                val currentSensors = allSensors?.filter { sensor ->
                    availableSensors.firstOrNull { availableSensor ->
                        sensor.id == availableSensor.id
                    } != null
                }

                items(availableSensors.size, { availableSensors[it].id }) { index ->
                    val basicSensor = availableSensors[index]
                    val sensor = currentSensors?.firstOrNull { sensor ->
                        sensor.id == basicSensor.id
                    }
                    SensorUi(
                        sensor = sensor,
                        manager = sensorManager,
                        basicSensor = basicSensor,
                    ) { sensorId, enabled -> onSensorClicked(sensorId, enabled) }
//                            isHapticEnabled,
//                            isToastEnabled

                }
            }
        }
    }
}

//@ExperimentalComposeUiApi
//@ExperimentalWearMaterialApi
//@ExperimentalAnimationApi
//@Preview
//@Composable
//private fun PreviewSensorUI() {
//    Column {
//        SensorManagerUi(
//            sensorDao,
//            sensorManager = previewSensorManager,
//        ) { _, _ -> }
////            isHapticEnabled = true,
////            isToastEnabled = false
//
//    }
//}
