package io.homeassistant.companion.android.home.views

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.wear.compose.material.ExperimentalWearMaterialApi
import androidx.wear.compose.material.TimeText

@Composable
@ExperimentalWearMaterialApi
@ExperimentalAnimationApi
fun TimeText(
    visible: Boolean
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(),
        exit = slideOutVertically(),
    ) {
        TimeText()
    }
}
