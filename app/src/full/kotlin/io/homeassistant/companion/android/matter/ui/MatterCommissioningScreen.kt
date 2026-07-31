package io.homeassistant.companion.android.matter.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.composable.HATopBarPlaceholder
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.MaxButtonWidth
import io.homeassistant.companion.android.matter.MatterCommissioningViewModel.CommissioningFlowStep
import io.homeassistant.companion.android.settings.server.ServerChooser
import io.homeassistant.companion.android.settings.server.ServerChooserItem

private val MaxContentWidth = MaxButtonWidth

/**
 * Renders the Matter commissioning flow for the given [step]: a loading indicator while the flow
 * is being prepared or a device is being commissioned, a [ServerChooser] sheet when multiple
 * servers are available, and status messages with their actions for the remaining steps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatterCommissioningScreen(
    step: CommissioningFlowStep,
    deviceName: String?,
    serverChooserItems: List<ServerChooserItem>,
    onSelectServer: (Int) -> Unit,
    onConfirmCommissioning: () -> Unit,
    onClose: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (step == CommissioningFlowStep.NotStarted) return

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        modifier = modifier,
    ) { contentPadding ->
        MatterCommissioningContent(
            step = step,
            deviceName = deviceName,
            onConfirmCommissioning = onConfirmCommissioning,
            onClose = onClose,
            onContinue = onContinue,
            modifier = Modifier.padding(contentPadding),
        )

        if (step == CommissioningFlowStep.SelectServer) {
            ServerChooser(
                items = serverChooserItems,
                onServerSelected = onSelectServer,
                onDismissRequest = onClose,
            )
        }
    }
}

@Composable
private fun MatterCommissioningContent(
    step: CommissioningFlowStep,
    deviceName: String?,
    onConfirmCommissioning: () -> Unit,
    onClose: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val loadingSteps = listOf(
        CommissioningFlowStep.NotStarted,
        CommissioningFlowStep.CheckingCore,
        CommissioningFlowStep.Working,
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HADimens.SPACE4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE6),
    ) {
        HATopBarPlaceholder()

        MatterCommissioningHeader()

        if (step in loadingSteps) {
            LoadingStatus(showWorkingText = step is CommissioningFlowStep.Working)
        } else if (step != CommissioningFlowStep.SelectServer) {
            StepStatusText(step = step, deviceName = deviceName)
        }

        Spacer(modifier = Modifier.weight(1f))

        if (step !in loadingSteps && step != CommissioningFlowStep.SelectServer) {
            StepButtons(
                step = step,
                onConfirmCommissioning = onConfirmCommissioning,
                onClose = onClose,
                onContinue = onContinue,
            )
        }
    }
}

@Composable
private fun ColumnScope.MatterCommissioningHeader() {
    Image(
        imageVector = ImageVector.vectorResource(R.drawable.ic_matter),
        contentDescription = null,
        modifier = Modifier
            .padding(top = HADimens.SPACE6)
            .padding(all = HADimens.SPACE5)
            .size(120.dp),
    Text(
        text = stringResource(commonR.string.matter_shared_title),
        style = HATextStyle.Headline,
        modifier = Modifier.widthIn(max = MaxContentWidth),
    )
}

@Composable
private fun ColumnScope.LoadingStatus(showWorkingText: Boolean) {
    HALoading()
    if (showWorkingText) {
        Text(
            text = stringResource(commonR.string.matter_shared_status_working),
            style = HATextStyle.Body,
            modifier = Modifier.widthIn(max = MaxContentWidth),
        )
    }
}

@Composable
private fun StepStatusText(step: CommissioningFlowStep, deviceName: String?, modifier: Modifier = Modifier) {
    Text(
        text = when (step) {
            CommissioningFlowStep.NotRegistered -> stringResource(
                commonR.string.matter_shared_status_not_registered,
            )
            CommissioningFlowStep.NotSupported -> stringResource(
                commonR.string.matter_shared_status_not_supported,
            )
            CommissioningFlowStep.Confirmation -> {
                if (deviceName?.isNotBlank() == true) {
                    stringResource(commonR.string.matter_shared_status_confirmation_named, deviceName)
                } else {
                    stringResource(commonR.string.matter_shared_status_confirmation)
                }
            }
            CommissioningFlowStep.Success -> stringResource(
                commonR.string.matter_shared_status_success,
            )
            is CommissioningFlowStep.Failure -> {
                if (step.errorCode != null) {
                    stringResource(commonR.string.matter_shared_status_failure_code, step.errorCode)
                } else {
                    stringResource(commonR.string.matter_shared_status_failure)
                }
            }
            else -> "" // not used: loading steps render LoadingStatus, SelectServer the ServerChooser sheet
        },
        style = HATextStyle.Body,
        modifier = modifier.widthIn(max = MaxContentWidth),
    )
}

/**
 * Renders the actions for [step] as a vertical stack of full-width buttons, the primary action on
 * top, like the onboarding screens.
 */
@Composable
private fun StepButtons(
    step: CommissioningFlowStep,
    onConfirmCommissioning: () -> Unit,
    onClose: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = HADimens.SPACE6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
    ) {
        val buttonModifier = Modifier
            .fillMaxWidth()
            .widthIn(max = MaxContentWidth)
        when (step) {
            CommissioningFlowStep.NotRegistered,
            CommissioningFlowStep.NotSupported,
            -> {
                HAAccentButton(
                    text = stringResource(commonR.string.close),
                    onClick = onClose,
                    modifier = buttonModifier,
                )
            }
            CommissioningFlowStep.Confirmation -> {
                HAAccentButton(
                    text = stringResource(commonR.string.add_device),
                    onClick = onConfirmCommissioning,
                    modifier = buttonModifier,
                )
                HAPlainButton(
                    text = stringResource(commonR.string.cancel),
                    onClick = onClose,
                    modifier = buttonModifier,
                )
            }
            CommissioningFlowStep.Success -> {
                HAAccentButton(
                    text = stringResource(commonR.string.continue_connect),
                    onClick = onContinue,
                    modifier = buttonModifier,
                )
            }
            is CommissioningFlowStep.Failure -> {
                HAAccentButton(
                    text = stringResource(commonR.string.retry),
                    onClick = onConfirmCommissioning,
                    modifier = buttonModifier,
                )
                HAPlainButton(
                    text = stringResource(commonR.string.cancel),
                    onClick = onClose,
                    modifier = buttonModifier,
                )
            }
            else -> { /* No button */ }
        }
    }
}

@Preview
@Composable
private fun PreviewMatterCommissioningScreen(
    @PreviewParameter(MatterCommissioningScreenPreviewStates::class) step: CommissioningFlowStep,
) {
    HAThemeForPreview {
        MatterCommissioningScreen(
            step = step,
            deviceName = "Manufacturer Matter Light",
            serverChooserItems = listOf(
                ServerChooserItem(serverId = 1, userName = "Alice Smith", serverName = "Home", isActive = true),
                ServerChooserItem(serverId = 2, userName = "Bob", serverName = "Friends home"),
            ),
            onSelectServer = { },
            onConfirmCommissioning = { },
            onClose = { },
            onContinue = { },
        )
    }
}
