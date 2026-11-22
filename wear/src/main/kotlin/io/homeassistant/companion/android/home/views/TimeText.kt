package io.homeassistant.companion.android.home.views

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.TimeSource
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.TimeTextDefaults
import androidx.wear.compose.material.scrollAway
import androidx.wear.tooling.preview.devices.WearDevices

@Composable
fun TimeText(scalingLazyListState: ScalingLazyListState) {
    TimeText(
        modifier = Modifier.scrollAway(scrollState = scalingLazyListState),
        timeSource = if (LocalInspectionMode.current) {
            object : TimeSource {
                override val currentTime: String
                    @Composable get() = "10:10"
            }
        } else {
            TimeTextDefaults.timeSource(TimeTextDefaults.timeFormat())
        },
    )
}

@Preview(device = WearDevices.LARGE_ROUND)
@Composable
private fun PreviewTimeText() {
    CompositionLocalProvider {
        TimeText(scalingLazyListState = rememberScalingLazyListState())
    }
}
