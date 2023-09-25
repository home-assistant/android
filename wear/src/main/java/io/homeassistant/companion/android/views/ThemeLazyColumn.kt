package io.homeassistant.companion.android.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.rotaryinput.rotaryWithScroll

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun ThemeLazyColumn(
    state: ScalingLazyListState = rememberScalingLazyListState(),
    content: ScalingLazyListScope.() -> Unit
) {
    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .rotaryWithScroll(state),
        contentPadding = PaddingValues(
            start = 8.dp,
            end = 8.dp
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        state = state,
        content = content
    )
}
