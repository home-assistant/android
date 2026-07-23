package io.homeassistant.companion.android.loading

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.util.compose.HAPreviews

/**
 * Size of the launch screen icon so the logo matches the system splash screen:
 * 112dp on API 31+ (`drawable-v31/app_icon_launch_screen.xml`, 112dp logo on the 288dp
 * canvas drawn by the system) and on older versions (`drawable-v26/launch_screen_background.xml`).
 */
private val ICON_SIZE = 112.dp

/** Height of the Open Home Foundation logo, matching the frontend launch screen. */
private val OHF_LOGO_HEIGHT = 46.dp

/** Opacity of the Open Home Foundation logo, matching the frontend launch screen. */
private const val OHF_LOGO_ALPHA = 0.66f

/**
 * Duration of the fade-in of the branding. The system splash screen only shows the centered icon,
 * so everything else eases in instead of popping when this screen takes over.
 */
private const val CONTENT_FADE_IN_MILLIS = 350

/** Viewport size of `app_icon_launch`, the coordinate space of [LOGO_DOTS]. */
private const val LOGO_VIEWPORT = 120f

/** Radius of the three logo dots in `app_icon_launch`. */
private const val LOGO_DOT_RADIUS = 10.25f

/** White of the logo artwork in `app_icon_launch`, so the pulsing dots blend into it at rest. */
private val LOGO_DOT_COLOR = Color(0xFFF2F4F9)

/** One full pulse sequence across the three logo dots. */
private const val PULSE_CYCLE_MILLIS = 1300

/** Time a dot takes to grow to [PULSE_MAX_SCALE]. */
private const val PULSE_GROW_MILLIS = 200

/** Time a dot takes to settle back to its resting size. */
private const val PULSE_SHRINK_MILLIS = 300

/** Delay between the pulses of consecutive dots. */
private const val PULSE_STAGGER_MILLIS = 200

private const val PULSE_MAX_SCALE = 1.2f

/** Ease-out curve of each grow and shrink step, matching the frontend loading animation. */
private val PulseEasing = CubicBezierEasing(0.39f, 0.575f, 0.565f, 1f)

/** A pulsing dot of the logo: its center in the [LOGO_VIEWPORT] space and when its pulse starts. */
private data class LogoDot(val center: Offset, val pulseStartMillis: Int)

/** The three dots of `app_icon_launch`, pulsing bottom-left, then right, then top. */
private val LOGO_DOTS = listOf(
    LogoDot(center = Offset(30f, 89.88f), pulseStartMillis = 0),
    LogoDot(center = Offset(90f, 72.88f), pulseStartMillis = PULSE_STAGGER_MILLIS),
    LogoDot(center = Offset(60f, 41.89f), pulseStartMillis = 2 * PULSE_STAGGER_MILLIS),
)

/** Minimum gap kept between the icon and the branding when the icon slides up. */
private val MIN_ICON_SPACING = HADimens.SPACE6

@Composable
fun LoadingScreen(modifier: Modifier = Modifier, showBrand: Boolean = false) {
    // Skip the fade-in in previews and screenshot tests, which capture the first frame
    val initialAlpha = if (LocalInspectionMode.current) 1f else 0f
    val brandAlpha = remember { Animatable(initialAlpha) }
    LaunchedEffect(Unit) {
        brandAlpha.animateTo(1f, tween(durationMillis = CONTENT_FADE_IN_MILLIS))
    }
    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize(),
    ) {
        val navigationBarsPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val brandBottomPadding = max(navigationBarsPadding, HADimens.SPACE12)

        LoadingIcon(offset = if (showBrand) iconOffset(maxHeight, brandBottomPadding) else 0.dp)
        if (showBrand) {
            Image(
                imageVector = ImageVector.vectorResource(commonR.drawable.ohf_badge),
                contentDescription = null,
                alpha = OHF_LOGO_ALPHA,
                colorFilter = ColorFilter.tint(LocalHAColorScheme.current.colorOnNeutralNormal),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = brandBottomPadding)
                    .height(OHF_LOGO_HEIGHT)
                    .graphicsLayer { alpha = brandAlpha.value },
            )
        }
    }
}

/**
 * App icon, centered so it lines up with the system splash screen and rising to [offset] as soon as
 * this screen takes over when the height leaves too little room for the branding below it. The three
 * dots of the logo pulse in sequence, drawn over their static counterparts in the drawable so the
 * icon is identical to the splash screen at rest.
 */
@Composable
private fun LoadingIcon(offset: Dp, modifier: Modifier = Modifier) {
    // Previews and screenshot tests capture the first frame, so they start settled
    val initialOffset = if (LocalInspectionMode.current) offset else 0.dp
    val animatedOffset = remember { Animatable(initialOffset, Dp.VectorConverter) }
    LaunchedEffect(offset) {
        animatedOffset.animateTo(offset, tween(durationMillis = CONTENT_FADE_IN_MILLIS))
    }
    val dotScales = logoDotScales()
    Image(
        imageVector = ImageVector.vectorResource(R.drawable.app_icon_launch),
        contentDescription = stringResource(commonR.string.loading_content_description),
        modifier = modifier
            .size(ICON_SIZE)
            .graphicsLayer { translationY = -animatedOffset.value.toPx() }
            .drawWithContent {
                drawContent()
                val scale = size.width / LOGO_VIEWPORT
                LOGO_DOTS.forEachIndexed { index, dot ->
                    drawCircle(
                        color = LOGO_DOT_COLOR,
                        radius = LOGO_DOT_RADIUS * scale * dotScales[index].value,
                        center = dot.center * scale,
                    )
                }
            },
    )
}

/** Scale of each entry of [LOGO_DOTS] over time, each dot pulsing once per cycle. */
@Composable
private fun logoDotScales(): List<State<Float>> {
    val transition = rememberInfiniteTransition(label = "logoDots")
    return LOGO_DOTS.map { dot ->
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                keyframes {
                    durationMillis = PULSE_CYCLE_MILLIS
                    1f at dot.pulseStartMillis using PulseEasing
                    PULSE_MAX_SCALE at dot.pulseStartMillis + PULSE_GROW_MILLIS using PulseEasing
                    1f at dot.pulseStartMillis + PULSE_GROW_MILLIS + PULSE_SHRINK_MILLIS
                },
            ),
            label = "logoDot${dot.pulseStartMillis}",
        )
    }
}

/**
 * How far the icon has to rise from its centered position to keep [MIN_ICON_SPACING] above the
 * branding.
 *
 * The icon is centered like the system splash screen, but short screens (landscape phones
 * especially) leave too little room below it, so it moves up by exactly the amount needed instead
 * of being overlapped by the branding.
 */
private fun iconOffset(screenHeight: Dp, brandBottomPadding: Dp): Dp {
    val iconBottom = screenHeight / 2 + ICON_SIZE / 2
    val brandTop = screenHeight - brandBottomPadding - OHF_LOGO_HEIGHT
    val spaceAboveIcon = (screenHeight / 2 - ICON_SIZE / 2).coerceAtLeast(0.dp)
    return (iconBottom + MIN_ICON_SPACING - brandTop).coerceIn(0.dp, spaceAboveIcon)
}

@HAPreviews
@Composable
private fun LoadingScreenPreview() {
    HAThemeForPreview {
        LoadingScreen()
    }
}

@HAPreviews
@Composable
private fun LoadingScreenPreviewWithBranding() {
    HAThemeForPreview {
        LoadingScreen(showBrand = true)
    }
}
