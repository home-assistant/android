package io.homeassistant.companion.android.home.views

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ExperimentalWearMaterialApi
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.ScalingLazyListState
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.rememberScalingLazyListState
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.sensors.SensorReceiver
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.util.LocalRotaryEventDispatcher
import io.homeassistant.companion.android.util.RotaryEventDispatcher
import io.homeassistant.companion.android.util.RotaryEventState
import io.homeassistant.companion.android.common.R as commonR

@ExperimentalAnimationApi
@ExperimentalWearMaterialApi
@Composable
fun SensorsView() {
    val scalingLazyListState: ScalingLazyListState = rememberScalingLazyListState()
    RotaryEventState(scrollState = scalingLazyListState)

    WearAppTheme {
        Scaffold(
            positionIndicator = {
                if (scalingLazyListState.isScrollInProgress)
                    PositionIndicator(scalingLazyListState = scalingLazyListState)
            },
            timeText = { TimeText(!scalingLazyListState.isScrollInProgress) }
        ) {
            val sensorManagers = getSensorManagers()
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
                    ListHeader(id = commonR.string.sensors)
                }
                items(sensorManagers.size, { sensorManagers[it].name } ) { index ->
                    sensorManagers.forEach { manager ->
                        Row {
                            Chip(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                colors = ChipDefaults.secondaryChipColors(),
                                label = {
                                    Text(
                                        text = stringResource(manager.name)
                                    )
                                },
                                onClick = {}
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun getSensorManagers(): List<SensorManager> {
    return SensorReceiver.MANAGERS.sortedBy { stringResource(it.name) }.filter { it.hasSensor(LocalContext.current) }
}

@ExperimentalAnimationApi
@ExperimentalWearMaterialApi
@Preview
@Composable
private fun PreviewSensorsView() {
    val rotaryEventDispatcher = RotaryEventDispatcher()

    CompositionLocalProvider(
        LocalRotaryEventDispatcher provides rotaryEventDispatcher
    ) {
        SensorsView()
    }
}
