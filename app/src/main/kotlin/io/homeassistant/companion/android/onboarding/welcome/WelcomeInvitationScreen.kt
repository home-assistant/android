package io.homeassistant.companion.android.onboarding.welcome

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HABanner
import io.homeassistant.companion.android.common.compose.composable.HATopBar
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.util.compose.HAPreviews
import kotlinx.coroutines.launch

private val INFO_ICON_SIZE = 16.dp

@Composable
internal fun WelcomeInvitationScreen(
    serverUrl: String,
    onAcceptClick: () -> Unit,
    onRejectClick: () -> Unit,
    onLearnMoreClick: suspend () -> Unit,
    modifier: Modifier = Modifier,
) {
    WelcomeTemplate(
        title = stringResource(commonR.string.welcome_invitation_title),
        details = stringResource(commonR.string.welcome_details),
        primaryButtonText = stringResource(commonR.string.welcome_invitation_accept),
        onPrimaryClick = onAcceptClick,
        secondaryButtonText = stringResource(commonR.string.welcome_invitation_reject),
        onSecondaryClick = onRejectClick,
        modifier = modifier,
        topBar = { HATopBar(onHelpClick = onLearnMoreClick) },
    ) {
        InvitationWarning(serverUrl = serverUrl)
    }
}

@Composable
private fun InvitationWarning(serverUrl: String) {
    HABanner(modifier = Modifier.widthIn(max = WelcomeContentMaxWidth)) {
        Column {
            Row {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
                    Text(
                        text = stringResource(commonR.string.welcome_invitation_server_label),
                        style = HATextStyle.Body.copy(textAlign = TextAlign.Start),
                    )
                    ServerAddressLink(serverUrl = serverUrl)
                }
                Icon(
                    imageVector = Icons.Default.MarkEmailUnread,
                    contentDescription = null,
                    tint = LocalHAColorScheme.current.colorFillPrimaryLoudResting,
                    modifier = Modifier.align(Alignment.Top),
                )
            }
            InvitationDetail(modifier = Modifier.padding(top = HADimens.SPACE8))
        }
    }
}

@Composable
private fun InvitationDetail(modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = LocalHAColorScheme.current.colorOnNeutralQuiet,
            modifier = Modifier.size(INFO_ICON_SIZE),
        )
        Text(
            text = stringResource(commonR.string.welcome_invitation_warning_detail),
            style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start),
        )
    }
}

@Composable
private fun ServerAddressLink(serverUrl: String) {
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val linkStyles = HATextStyle.Link

    val annotatedAddress = buildAnnotatedString {
        withLink(
            LinkAnnotation.Clickable(tag = serverUrl, styles = linkStyles) {
                coroutineScope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(serverUrl, serverUrl)))
                }
            },
        ) {
            append(serverUrl)
        }
    }

    Text(
        text = annotatedAddress,
        style = HATextStyle.Body.copy(textAlign = TextAlign.Start),
    )
}

@HAPreviews
@Composable
private fun WelcomeInvitationScreenPreview() {
    HAThemeForPreview {
        WelcomeInvitationScreen(
            serverUrl = "http://homeassistant.local:8123",
            onAcceptClick = {},
            onRejectClick = {},
            onLearnMoreClick = {},
        )
    }
}
