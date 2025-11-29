package io.homeassistant.companion.android.onboarding.localfirst

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.MaxButtonWidth
import io.homeassistant.companion.android.compose.HAPreviews
import io.homeassistant.companion.android.compose.composable.HATopBarPlaceholder
import io.homeassistant.companion.android.onboarding.R

private val MaxContentWidth = MaxButtonWidth

@Composable
internal fun LocalFirstScreen(onNextClick: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier, contentWindowInsets = WindowInsets.safeDrawing) { contentPadding ->
        LocalFirstContent(
            onNextClick = onNextClick,
            modifier = Modifier.padding(contentPadding),
        )
    }
}

@Composable
private fun LocalFirstContent(onNextClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HADimens.SPACE4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE6),
    ) {
        // Fake Space at the top since we don't have a topbar on this screen.
        // It makes the image bellow perfectly aligned with the other screens.
        HATopBarPlaceholder()

        Image(
            modifier = Modifier.padding(top = HADimens.SPACE6),
            // Use painterResource instead of vector resource for API < 24 since it has gradients
            painter = painterResource(R.drawable.ic_no_remote),
            contentDescription = null,
        )

        Text(
            text = stringResource(commonR.string.local_first_title),
            style = HATextStyle.Headline,
            modifier = Modifier.widthIn(max = MaxContentWidth),
        )

        Text(
            text = stringResource(commonR.string.local_first_content),
            style = HATextStyle.Body,
            modifier = Modifier.widthIn(max = MaxContentWidth),
        )

        Spacer(modifier = Modifier.weight(1f))

        HAAccentButton(
            text = stringResource(commonR.string.local_first_next),
            onClick = onNextClick,
            modifier = Modifier.fillMaxWidth().padding(bottom = HADimens.SPACE6),
        )
    }
}

@HAPreviews
@Composable
private fun LocalFirstScreenPreview() {
    HAThemeForPreview {
        LocalFirstScreen(onNextClick = {})
    }
}
