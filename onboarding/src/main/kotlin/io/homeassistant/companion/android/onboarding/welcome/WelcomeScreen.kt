package io.homeassistant.companion.android.onboarding.welcome

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.homeassistant.companion.android.compose.HAPreviews
import io.homeassistant.companion.android.onboarding.R
import io.homeassistant.companion.android.onboarding.theme.HARadius
import io.homeassistant.companion.android.onboarding.theme.HASpacing
import io.homeassistant.companion.android.onboarding.theme.HATextStyle
import io.homeassistant.companion.android.onboarding.theme.HATheme
import io.homeassistant.companion.android.onboarding.theme.MaxButtonWidth

@Composable
fun WelcomeScreen(
    modifier: Modifier = Modifier,
    onConnectClick: () -> Unit = {},
    onLearnMoreClick: () -> Unit = {},
    viewModel: WelcomeViewModel = hiltViewModel(),
) {
    val paddingModifier = Modifier.padding(horizontal = HASpacing.M, vertical = HASpacing.S)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // We use spacer to position the image where we want when there is remaining space in the column using percentage
        val positionPercentage = 0.2f
        Spacer(modifier = Modifier.weight(positionPercentage))

        Image(
            imageVector = ImageVector.vectorResource(R.drawable.ic_home_assistant_branding),
            contentDescription = stringResource(R.string.home_assistant_branding_icon_content_description),
            // TODO should be in a variable
            modifier = paddingModifier.size(120.dp),
        )
        WelcomeText(paddingModifier)

        Spacer(modifier = Modifier.weight(1f - positionPercentage))

        BottomButtons(onConnectClick = onConnectClick, onLearnMoreClick = onLearnMoreClick)
    }
}

@Composable
private fun ColumnScope.WelcomeText(modifier: Modifier) {
    Text(
        text = stringResource(R.string.welcome_home_assistant_title),
        style = HATextStyle.Headline,
        modifier = modifier,
    )

    Text(
        text = stringResource(R.string.welcome_details),
        style = HATextStyle.Body,
        modifier = modifier,
    )
}

@Composable
private fun BottomButtons(
    onConnectClick: () -> Unit,
    onLearnMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val buttonModifier = modifier
        .padding(horizontal = HASpacing.M)
        .widthIn(max = MaxButtonWidth)
        .fillMaxWidth()

    Button(
        onClick = onConnectClick,
        modifier = buttonModifier.padding(bottom = HASpacing.XS),
        contentPadding = PaddingValues(horizontal = HASpacing.XL, vertical = HASpacing.M),
        shape = RoundedCornerShape(size = HARadius.XL),
    ) {
        Text(
            text = stringResource(R.string.welcome_connect_to_ha),
            style = HATextStyle.Button,
        )
    }

    TextButton(
        onClick = onLearnMoreClick,
        modifier = buttonModifier.padding(bottom = HASpacing.XL),
        contentPadding = PaddingValues(horizontal = HASpacing.XL, vertical = HASpacing.M),
        shape = RoundedCornerShape(size = HARadius.XL),
    ) {
        Text(
            text = stringResource(R.string.welcome_learn_more),
            style = HATextStyle.Button,
        )
    }
}

@HAPreviews
@Composable
private fun WelcomeScreenPreview() {
    HATheme {
        WelcomeScreen()
    }
}
