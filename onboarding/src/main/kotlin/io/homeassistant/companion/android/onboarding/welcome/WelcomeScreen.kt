package io.homeassistant.companion.android.onboarding.welcome

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.theme.HASpacing
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.compose.HAPreviews
import io.homeassistant.companion.android.onboarding.R

@Composable
internal fun WelcomeScreen(onConnectClick: () -> Unit, onLearnMoreClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = HASpacing.M),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HASpacing.XL),
    ) {
        // We use spacer to position the image where we want when there is remaining space in the column using percentage
        val positionPercentage = 0.2f
        Spacer(modifier = Modifier.weight(positionPercentage))

        Image(
            imageVector = ImageVector.vectorResource(R.drawable.ic_home_assistant_branding),
            contentDescription = stringResource(R.string.home_assistant_branding_icon_content_description),
            // TODO should be in a variable
            modifier = Modifier.size(120.dp),
        )
        WelcomeText()

        Spacer(modifier = Modifier.weight(1f - positionPercentage))

        BottomButtons(onConnectClick = onConnectClick, onLearnMoreClick = onLearnMoreClick)
    }
}

@Composable
private fun ColumnScope.WelcomeText() {
    Text(
        text = stringResource(R.string.welcome_home_assistant_title),
        style = HATextStyle.Headline,
    )

    Text(
        text = stringResource(R.string.welcome_details),
        style = HATextStyle.Body,
    )
}

@Composable
private fun ColumnScope.BottomButtons(onConnectClick: () -> Unit, onLearnMoreClick: () -> Unit) {
    HAAccentButton(
        text = stringResource(R.string.welcome_connect_to_ha),
        onClick = onConnectClick,
    )

    HAPlainButton(
        text = stringResource(R.string.welcome_learn_more),
        onClick = onLearnMoreClick,
        modifier = Modifier.padding(bottom = HASpacing.XL),
    )
}

@HAPreviews
@Composable
private fun WelcomeScreenPreview() {
    HATheme {
        WelcomeScreen(onConnectClick = {}, onLearnMoreClick = {})
    }
}
