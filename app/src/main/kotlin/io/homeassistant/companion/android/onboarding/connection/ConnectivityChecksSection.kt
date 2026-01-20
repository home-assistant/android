package io.homeassistant.companion.android.onboarding.connection

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HAFontSize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.data.connectivity.ConnectivityCheckResult
import io.homeassistant.companion.android.common.data.connectivity.ConnectivityCheckState

/**
 * Reusable connectivity checks section that displays the results of connectivity checks.
 * Can be embedded in error screens or standalone connectivity check screens.
 *
 * @param connectivityCheckState The current connectivity check state
 * @param onRetryConnectivityCheck Callback when retry is requested
 * @param modifier Optional modifier
 */
@Composable
fun ConnectivityChecksSection(
    connectivityCheckState: ConnectivityCheckState,
    onRetryConnectivityCheck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE3),
    ) {
        Text(
            text = stringResource(commonR.string.connection_check_title),
            style = HATextStyle.Body.copy(textAlign = TextAlign.Start),
        )

        CheckResultRow(
            label = stringResource(commonR.string.connection_check_dns),
            result = connectivityCheckState.dnsResolution,
        )

        CheckResultRow(
            label = stringResource(commonR.string.connection_check_port),
            result = connectivityCheckState.portReachability,
        )

        CheckResultRow(
            label = stringResource(commonR.string.connection_check_tls),
            result = connectivityCheckState.tlsCertificate,
        )

        CheckResultRow(
            label = stringResource(commonR.string.connection_check_server),
            result = connectivityCheckState.serverConnection,
        )

        CheckResultRow(
            label = stringResource(commonR.string.connection_check_home_assistant),
            result = connectivityCheckState.homeAssistantVerification,
        )

        HAAccentButton(
            text = stringResource(commonR.string.retry),
            onClick = onRetryConnectivityCheck,
            enabled = connectivityCheckState.isComplete,
            modifier = Modifier
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun CheckResultRow(label: String, result: ConnectivityCheckResult) {
    val iconTint by animateColorAsState(
        targetValue = when (result) {
            is ConnectivityCheckResult.Success -> LocalHAColorScheme.current.colorFillPrimaryLoudResting
            is ConnectivityCheckResult.Failure -> LocalHAColorScheme.current.colorOnDangerQuiet
            is ConnectivityCheckResult.NotApplicable,
            is ConnectivityCheckResult.InProgress,
            is ConnectivityCheckResult.Pending,
            -> LocalHAColorScheme.current.colorOnNeutralQuiet
        },
        label = "iconTint",
    )

    val iconModifier = Modifier.size(24.dp)
    val textStyle = HATextStyle.BodyMedium.copy(fontSize = HAFontSize.S)

    Row(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = result,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "iconAnimation",
        ) { animatedResult ->
            when (animatedResult) {
                is ConnectivityCheckResult.Success -> Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = stringResource(commonR.string.successful),
                    modifier = iconModifier,
                    tint = iconTint,
                )
                is ConnectivityCheckResult.Failure -> Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = stringResource(commonR.string.state_error),
                    modifier = iconModifier,
                    tint = iconTint,
                )
                is ConnectivityCheckResult.NotApplicable -> Icon(
                    imageVector = Icons.Outlined.Circle,
                    contentDescription = stringResource(
                        commonR.string.not_applicable_content_description,
                    ),
                    modifier = iconModifier,
                    tint = iconTint,
                )
                is ConnectivityCheckResult.InProgress,
                is ConnectivityCheckResult.Pending,
                -> CircularProgressIndicator(
                    modifier = iconModifier,
                    color = iconTint,
                    strokeWidth = 2.dp,
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = HATextStyle.BodyMedium,
            )

            AnimatedContent(
                targetState = result,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "resultContent",
            ) { animatedResult ->
                when (animatedResult) {
                    is ConnectivityCheckResult.Success -> {
                        Text(
                            text = animatedResult.details ?: stringResource(animatedResult.messageResId),
                            style = textStyle,
                            color = LocalHAColorScheme.current.colorTextSecondary,
                        )
                    }

                    is ConnectivityCheckResult.Failure -> {
                        Text(
                            text = stringResource(animatedResult.messageResId),
                            style = textStyle,
                            color = LocalHAColorScheme.current.colorOnDangerQuiet,
                        )
                    }

                    is ConnectivityCheckResult.NotApplicable -> {
                        Text(
                            text = stringResource(animatedResult.messageResId),
                            style = textStyle,
                            color = LocalHAColorScheme.current.colorTextSecondary,
                        )
                    }

                    is ConnectivityCheckResult.InProgress,
                    is ConnectivityCheckResult.Pending,
                    -> {
                        Text(
                            text = stringResource(commonR.string.loading),
                            style = textStyle,
                            color = LocalHAColorScheme.current.colorOnNeutralQuiet,
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun PreviewConnectivityChecksSection() {
    HAThemeForPreview {
        ConnectivityChecksSection(
            connectivityCheckState = ConnectivityCheckState(),
            onRetryConnectivityCheck = {},
        )
    }
}

@Preview
@Composable
private fun PreviewConnectivityChecksSectionMixed() {
    HAThemeForPreview {
        ConnectivityChecksSection(
            connectivityCheckState = ConnectivityCheckState(
                dnsResolution = ConnectivityCheckResult.Success(commonR.string.connection_check_dns, "192.168.1.10"),
                portReachability = ConnectivityCheckResult.Success(commonR.string.connection_check_port, "80"),
                tlsCertificate = ConnectivityCheckResult.NotApplicable(
                    commonR.string.connection_check_tls_not_applicable,
                ),
                serverConnection = ConnectivityCheckResult.Failure(commonR.string.connection_check_error_server),
                homeAssistantVerification = ConnectivityCheckResult.Pending,
            ),
            onRetryConnectivityCheck = {},
        )
    }
}
