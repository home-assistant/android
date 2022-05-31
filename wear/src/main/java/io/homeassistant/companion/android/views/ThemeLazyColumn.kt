package io.homeassistant.companion.android.home.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.ScalingLazyListScope
import androidx.wear.compose.material.ScalingLazyListState
import androidx.wear.compose.material.rememberScalingLazyListState
import io.homeassistant.companion.android.util.rotaryEventHandler

@Composable
fun ThemeLazyColumn(
    state: ScalingLazyListState = rememberScalingLazyListState(),
    content: ScalingLazyListScope.() -> Unit
) {
    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .rotaryEventHandler(state),
        contentPadding = PaddingValues(
            top = 24.dp,
            start = 8.dp,
            end = 8.dp,
            bottom = 48.dp
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        state = state,
        content = content
    )
}
