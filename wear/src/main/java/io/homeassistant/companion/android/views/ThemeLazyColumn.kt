package io.homeassistant.companion.android.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
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
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .rotaryWithScroll(state, focusRequester),
        contentPadding = PaddingValues(
            start = 8.dp,
            end = 8.dp
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        state = state,
        content = content
    )

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}
