package io.homeassistant.companion.android.compose.composable

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.onboarding.R

private val ICON_SIZE = 64.dp

@Composable
fun HALoading(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "HALoading")
    val yOffset by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "yOffset",
    )
    val rotation by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "rotation",
    )

    Box(
        modifier = modifier
            .defaultMinSize(
                minWidth = ICON_SIZE,
                minHeight = ICON_SIZE + 20.dp, // Add space for bounce
            )
            .then(Modifier.size(ICON_SIZE)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            imageVector = ImageVector.vectorResource(R.drawable.ic_home_assistant_branding),
            contentDescription = stringResource(R.string.loading_content_description),
            modifier = Modifier
                .size(ICON_SIZE)
                .align(Alignment.Center)
                .offset { IntOffset(0, yOffset.toInt()) }
                .graphicsLayer(rotationZ = rotation),
        )
    }
}
