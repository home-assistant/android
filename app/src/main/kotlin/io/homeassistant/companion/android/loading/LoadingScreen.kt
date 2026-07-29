package io.homeassistant.companion.android.loading

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HALoading
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
 * Duration of the fade-in of the loading indicator and logo. The system splash screen only shows
 * the centered icon, so everything else eases in instead of popping when this screen takes over.
 */
private const val CONTENT_FADE_IN_MILLIS = 350

/**
 * Size of the loading indicator, so the layout below the icon can be computed up front rather than
 * measured, which would only settle on the second frame.
 *
 * This repeats the Material 3 default rather than reusing it: the value lives in
 * `CircularProgressIndicatorTokens.Size`, which is internal, and `ProgressIndicatorDefaults` exposes
 * no size. If a Material update changes it, [HALoading] keeps rendering at the size below.
 */
private val LOADING_SIZE = 40.dp

/** Minimum gap kept between the icon and the loading indicator when the icon slides up. */
private val MIN_ICON_SPACING = HADimens.SPACE6

/** Minimum gap kept between the loading indicator and the branding. */
private val MIN_BRAND_SPACING = HADimens.SPACE4

/** Vertical metrics of [LoadingScreen], derived from the height it is given. */
private data class LoadingScreenLayout(
    /** Distance between the bottom of the screen and the content stacked there. */
    val bottomPadding: Dp,
    /** Gap between the loading indicator and the branding. */
    val brandSpacing: Dp,
    /** How far the icon rises from the center to clear the content below it. */
    val iconOffset: Dp,
)

@Composable
fun LoadingScreen(modifier: Modifier = Modifier, showBrand: Boolean = false) {
    // Skip the fade-in in previews and screenshot tests, which capture the first frame
    val initialAlpha = if (LocalInspectionMode.current) 1f else 0f
    val contentAlpha = remember { Animatable(initialAlpha) }
    LaunchedEffect(Unit) {
        contentAlpha.animateTo(1f, tween(durationMillis = CONTENT_FADE_IN_MILLIS))
    }
    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize(),
    ) {
        val navigationBarsPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val layout = loadingScreenLayout(
            screenHeight = maxHeight,
            brandBottomPadding = max(navigationBarsPadding, HADimens.SPACE12),
            showBrand = showBrand,
        )

        LoadingIcon(offset = layout.iconOffset)
        LoadingBottomContent(
            showBrand = showBrand,
            layout = layout,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer { alpha = contentAlpha.value },
        )
    }
}

/**
 * App icon, centered so it lines up with the system splash screen and rising to [offset] as soon as
 * this screen takes over when the height leaves too little room for the content below it.
 */
@Composable
private fun LoadingIcon(offset: Dp, modifier: Modifier = Modifier) {
    // Previews and screenshot tests capture the first frame, so they start settled
    val initialOffset = if (LocalInspectionMode.current) offset else 0.dp
    val animatedOffset = remember { Animatable(initialOffset, Dp.VectorConverter) }
    LaunchedEffect(offset) {
        animatedOffset.animateTo(offset, tween(durationMillis = CONTENT_FADE_IN_MILLIS))
    }
    Image(
        imageVector = ImageVector.vectorResource(R.drawable.app_icon_launch),
        contentDescription = null,
        modifier = modifier
            .size(ICON_SIZE)
            .graphicsLayer { translationY = -animatedOffset.value.toPx() },
    )
}

/**
 * Loading indicator and optional branding, stacked in a single column so they can never be drawn on
 * top of each other whatever the height of the screen.
 */
@Composable
private fun LoadingBottomContent(showBrand: Boolean, layout: LoadingScreenLayout, modifier: Modifier = Modifier) {
    val contentDescriptionLoading = stringResource(commonR.string.loading_content_description)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(bottom = layout.bottomPadding),
    ) {
        HALoading(
            modifier = Modifier
                .size(LOADING_SIZE)
                .semantics {
                    contentDescription = contentDescriptionLoading
                },
        )
        if (showBrand) {
            Spacer(modifier = Modifier.height(layout.brandSpacing))
            Image(
                imageVector = ImageVector.vectorResource(commonR.drawable.ohf_badge),
                contentDescription = null,
                alpha = OHF_LOGO_ALPHA,
                colorFilter = ColorFilter.tint(LocalHAColorScheme.current.colorOnNeutralNormal),
                modifier = Modifier.height(OHF_LOGO_HEIGHT),
            )
        }
    }
}

/**
 * Places the content below the icon, then works out how far the icon has to rise to clear it.
 *
 * The icon is centered like the system splash screen, but short screens (landscape phones
 * especially) leave too little room below it, so it moves up by exactly the amount needed to keep
 * [MIN_ICON_SPACING] above the loading indicator instead of being overlapped by it.
 */
private fun loadingScreenLayout(screenHeight: Dp, brandBottomPadding: Dp, showBrand: Boolean): LoadingScreenLayout {
    // On tall screens this reproduces the loading indicator sitting an eighth of the screen above
    // the bottom; on short ones the minimum keeps it clear of the branding.
    val brandSpacing = (screenHeight / 8 - brandBottomPadding - OHF_LOGO_HEIGHT)
        .coerceAtLeast(MIN_BRAND_SPACING)
    val bottomPadding = if (showBrand) brandBottomPadding else screenHeight / 8
    val bottomContentHeight = bottomPadding + LOADING_SIZE +
        if (showBrand) brandSpacing + OHF_LOGO_HEIGHT else 0.dp

    val iconBottom = screenHeight / 2 + ICON_SIZE / 2
    val spaceAboveIcon = (screenHeight / 2 - ICON_SIZE / 2).coerceAtLeast(0.dp)
    return LoadingScreenLayout(
        bottomPadding = bottomPadding,
        brandSpacing = brandSpacing,
        iconOffset = (iconBottom + MIN_ICON_SPACING - (screenHeight - bottomContentHeight))
            .coerceIn(0.dp, spaceAboveIcon),
    )
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
