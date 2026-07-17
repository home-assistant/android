package io.homeassistant.companion.android.loading

import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
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
        Image(
            imageVector = ImageVector.vectorResource(R.drawable.app_icon_launch),
            contentDescription = null,
            modifier = Modifier.size(ICON_SIZE),
        )
        val contentDescriptionLoading = stringResource(commonR.string.loading_content_description)
        HALoading(
            modifier = Modifier
                .padding(bottom = maxHeight / 8)
                .align(Alignment.BottomCenter)
                .graphicsLayer { alpha = contentAlpha.value }
                .semantics {
                    contentDescription = contentDescriptionLoading
                },
        )
        if (showBrand) {
            Image(
                imageVector = ImageVector.vectorResource(commonR.drawable.ohf_badge),
                contentDescription = null,
                alpha = OHF_LOGO_ALPHA,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        bottom = max(
                            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                            HADimens.SPACE12,
                        ),
                    )
                    .height(OHF_LOGO_HEIGHT)
                    .graphicsLayer { alpha = contentAlpha.value },
            )
        }
    }
}

@HAPreviews
@Composable
private fun LoadingScreenPreview() {
    HAThemeForPreview {
        LoadingScreen()
    }
}
