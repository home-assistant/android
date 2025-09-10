package io.homeassistant.companion.android.onboarding.localfirst

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.theme.HASpacing
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.MaxButtonWidth
import io.homeassistant.companion.android.compose.HAPreviews
import io.homeassistant.companion.android.compose.composable.HATopBar
import io.homeassistant.companion.android.onboarding.R

private val MaxContentWidth = MaxButtonWidth

@Composable
internal fun LocalFirstScreen(onBackClick: () -> Unit, onNextClick: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = { HATopBar(onBackClick = onBackClick) },
    ) { contentPadding ->
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
            .padding(horizontal = HASpacing.M),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HASpacing.XL),
    ) {
        Image(
            modifier = Modifier.padding(top = HASpacing.XL),
            imageVector = ImageVector.vectorResource(R.drawable.ic_no_remote),
            contentDescription = null,
        )

        Text(
            text = stringResource(R.string.local_first_title),
            style = HATextStyle.Headline,
            modifier = Modifier.widthIn(max = MaxContentWidth),
        )

        Text(
            text = stringResource(R.string.local_first_content),
            style = HATextStyle.Body,
            modifier = Modifier.widthIn(max = MaxContentWidth),
        )

        Spacer(modifier = Modifier.weight(1f))

        HAAccentButton(
            text = stringResource(R.string.local_first_next),
            onClick = onNextClick,
            modifier = Modifier.padding(bottom = HASpacing.XL),
        )
    }
}

@HAPreviews
@Composable
private fun LocalFirstScreenPreview() {
    HAThemeForPreview {
        LocalFirstScreen(onNextClick = {}, onBackClick = {})
    }
}
