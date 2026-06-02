package io.homeassistant.companion.android.onboarding.welcome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.util.compose.HAPreviews
import kotlinx.coroutines.launch

@Composable
internal fun WelcomeScreen(
    onConnectClick: () -> Unit,
    onLearnMoreClick: suspend () -> Unit,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()

    WelcomeTemplate(
        title = stringResource(commonR.string.welcome_home_assistant_title),
        details = stringResource(commonR.string.welcome_details),
        primaryButtonText = stringResource(commonR.string.welcome_connect_to_ha),
        onPrimaryClick = onConnectClick,
        secondaryButtonText = stringResource(commonR.string.welcome_learn_more),
        onSecondaryClick = { coroutineScope.launch { onLearnMoreClick() } },
        modifier = modifier,
    )
}

@HAPreviews
@Composable
private fun WelcomeScreenPreview() {
    HAThemeForPreview {
        WelcomeScreen(onConnectClick = {}, onLearnMoreClick = {})
    }
}
