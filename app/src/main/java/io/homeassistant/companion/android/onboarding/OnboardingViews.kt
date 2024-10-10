package io.homeassistant.companion.android.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.IIcon
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.util.compose.STEP_SCREEN_MAX_WIDTH
import kotlin.math.min

/**
 * Base layout for onboarding views which centers content and limits the container width if needed.
 */
@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        val screenWidth = LocalConfiguration.current.screenWidthDp
        Column(
            modifier = modifier
                .padding(all = 16.dp)
                .width(min(screenWidth, STEP_SCREEN_MAX_WIDTH).dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            content()
        }
    }
}

@Composable
fun OnboardingHeaderView(
    icon: IIcon,
    title: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(32.dp))
        Image(
            asset = icon,
            colorFilter = ColorFilter.tint(colorResource(commonR.color.colorAccent)),
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .align(Alignment.CenterHorizontally)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.h5,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(vertical = 16.dp)
                .align(Alignment.CenterHorizontally)
        )
    }
}
