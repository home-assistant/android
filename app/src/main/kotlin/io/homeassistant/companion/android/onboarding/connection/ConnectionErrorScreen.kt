package io.homeassistant.companion.android.onboarding.connection

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.frontend.error.FrontendErrorScreen
import io.homeassistant.companion.android.frontend.error.FrontendErrorStateProvider

@Composable
internal fun ConnectionErrorScreen(
    stateProvider: FrontendErrorStateProvider,
    onOpenExternalLink: suspend (Uri) -> Unit,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FrontendErrorScreen(
        stateProvider = stateProvider,
        onOpenExternalLink = onOpenExternalLink,
        modifier = modifier,
        actions = {
            CloseAction(onClick = onCloseClick)
        },
    )
}

@Composable
private fun CloseAction(onClick: () -> Unit) {
    HAAccentButton(
        stringResource(commonR.string.back),
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = HADimens.SPACE6),
    )
}
