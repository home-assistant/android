package io.homeassistant.companion.android.home.views

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material.ScalingLazyListState
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.rememberScalingLazyListState
import androidx.wear.compose.material.scrollAway

@Composable
fun TimeText(
    scalingLazyListState: ScalingLazyListState
) {
    TimeText(
        modifier = Modifier.scrollAway(scrollState = scalingLazyListState)
    )
}

@Preview(device = Devices.WEAR_OS_LARGE_ROUND)
@Composable
private fun PreviewTimeText() {
    CompositionLocalProvider {
        TimeText(scalingLazyListState = rememberScalingLazyListState())
    }
}
