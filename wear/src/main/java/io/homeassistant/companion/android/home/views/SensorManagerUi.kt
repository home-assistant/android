package io.homeassistant.companion.android.home.views

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.ExperimentalWearMaterialApi
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.ScalingLazyListState
import androidx.wear.compose.material.rememberScalingLazyListState
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.database.sensor.SensorDao
import io.homeassistant.companion.android.theme.WearAppTheme
//import io.homeassistant.companion.android.util.previewSensorManager

@ExperimentalAnimationApi
@ExperimentalWearMaterialApi
@Composable
@ExperimentalComposeUiApi
fun SensorManagerUi(
    sensorDao: SensorDao,
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
            val sensors = sensorManager.getAvailableSensors(LocalContext.current)
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

                items(sensors.size, { sensors[it].id }) { index ->
                    val basicSensor = sensors[index]
                    SensorUi(
                        sensorDao = sensorDao,
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
