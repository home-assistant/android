package io.homeassistant.companion.android.loading

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.compose.HAPreviews
import io.homeassistant.companion.android.onboarding.R

private val ICON_SIZE = 64.dp

@Composable
internal fun LoadingScreen(modifier: Modifier = Modifier) {
    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize(),
    ) {
        Image(
            imageVector = ImageVector.vectorResource(R.drawable.ic_home_assistant_branding),
            contentDescription = stringResource(R.string.loading_content_description),
            modifier = Modifier.size(ICON_SIZE),
        )
        HALoading(
            modifier = Modifier
                .padding(bottom = maxHeight / 8)
                .align(Alignment.BottomCenter),
        )
    }
}

@HAPreviews
@Composable
private fun LoadingScreenPreview() {
    HAThemeForPreview {
        LoadingScreen()
    }
}
