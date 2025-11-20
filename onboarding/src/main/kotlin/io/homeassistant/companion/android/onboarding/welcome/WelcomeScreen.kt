package io.homeassistant.companion.android.onboarding.welcome

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.MaxButtonWidth
import io.homeassistant.companion.android.compose.HAPreviews
import io.homeassistant.companion.android.onboarding.R

private val ICON_SIZE = 120.dp
private val MaxTextWidth = MaxButtonWidth

@Composable
internal fun WelcomeScreen(onConnectClick: () -> Unit, onLearnMoreClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = HADimens.SPACE4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE6),
    ) {
        // We use spacer to position the image where we want when there is remaining space in the column using percentage
        val positionPercentage = 0.2f
        Spacer(modifier = Modifier.weight(positionPercentage))

        Image(
            imageVector = ImageVector.vectorResource(R.drawable.ic_home_assistant_branding),
            contentDescription = stringResource(commonR.string.home_assistant_branding_icon_content_description),
            modifier = Modifier.size(ICON_SIZE),
        )
        WelcomeText()

        Spacer(modifier = Modifier.weight(1f - positionPercentage))

        BottomButtons(onConnectClick = onConnectClick, onLearnMoreClick = onLearnMoreClick)
    }
}

@Composable
private fun ColumnScope.WelcomeText() {
    Text(
        text = stringResource(commonR.string.welcome_home_assistant_title),
        style = HATextStyle.Headline,
        modifier = Modifier.widthIn(max = MaxTextWidth),
    )

    Text(
        text = stringResource(commonR.string.welcome_details),
        style = HATextStyle.Body,
        modifier = Modifier.widthIn(max = MaxTextWidth),
    )
}

@Composable
private fun BottomButtons(onConnectClick: () -> Unit, onLearnMoreClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
    ) {
        HAAccentButton(
            text = stringResource(commonR.string.welcome_connect_to_ha),
            onClick = onConnectClick,
            modifier = Modifier.fillMaxWidth(),
        )

        HAPlainButton(
            text = stringResource(commonR.string.welcome_learn_more),
            onClick = onLearnMoreClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = HADimens.SPACE6),
        )
    }
}

@HAPreviews
@Composable
private fun WelcomeScreenPreview() {
    HAThemeForPreview {
        WelcomeScreen(onConnectClick = {}, onLearnMoreClick = {})
    }
}
