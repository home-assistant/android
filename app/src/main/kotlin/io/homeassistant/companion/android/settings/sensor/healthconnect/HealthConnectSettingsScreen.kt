package io.homeassistant.companion.android.settings.sensor.healthconnect

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAHint
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.composable.HASettingsCard
import io.homeassistant.companion.android.common.compose.composable.HASwitch
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.util.plus
import io.homeassistant.companion.android.util.safeBottomPaddingValues

@Composable
fun HealthConnectSettingsScreen(viewModel: HealthConnectSettingsViewModel, modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HealthConnectSettingsContent(
        uiState = uiState,
        onToggleRealtimeSync = viewModel::setRealtimeSyncEnabled,
        onEnableAll = viewModel::enableAll,
        modifier = modifier,
    )
}

@Composable
@VisibleForTesting
internal fun HealthConnectSettingsContent(
    uiState: HealthConnectSettingsUiState,
    onToggleRealtimeSync: (Boolean) -> Unit,
    onEnableAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showEnableAllConfirm by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(all = HADimens.SPACE4) + safeBottomPaddingValues(applyHorizontal = false)),
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
    ) {
        when {
            uiState.isLoading -> HALoading(modifier = Modifier.align(Alignment.CenterHorizontally))
            !uiState.isAvailable -> HAHint(
                text = stringResource(commonR.string.health_connect_unavailable),
                modifier = Modifier.fillMaxWidth(),
            )
            else -> {
                RealtimeSyncSection(
                    enabled = uiState.realtimeSyncEnabled,
                    onToggle = onToggleRealtimeSync,
                )
                EnableAllSection(
                    inProgress = uiState.enableAllInProgress,
                    enabledCount = uiState.enabledSensorCount,
                    totalCount = uiState.totalSensorCount,
                    onClick = { showEnableAllConfirm = true },
                )
            }
        }
    }

    if (showEnableAllConfirm) {
        val haColors = LocalHAColorScheme.current
        AlertDialog(
            onDismissRequest = { showEnableAllConfirm = false },
            // Use HATextStyle for title / body — both pull their color from
            // LocalHAColorScheme.current.colorTextPrimary/Secondary, so dark mode
            // contrast comes out right. M3 AlertDialog's default styling pulls from
            // MaterialTheme.colorScheme which the HA theme doesn't override completely.
            title = {
                Text(
                    text = stringResource(commonR.string.health_connect_enable_all_confirm_title),
                    style = HATextStyle.HeadlineMedium,
                    color = haColors.colorTextPrimary,
                )
            },
            text = {
                Text(
                    text = stringResource(commonR.string.health_connect_enable_all_confirm_message),
                    style = HATextStyle.Body,
                    color = haColors.colorTextSecondary,
                )
            },
            confirmButton = {
                HAPlainButton(
                    text = stringResource(commonR.string.confirm),
                    onClick = {
                        showEnableAllConfirm = false
                        onEnableAll()
                    },
                )
            },
            dismissButton = {
                HAPlainButton(
                    text = stringResource(commonR.string.cancel),
                    onClick = { showEnableAllConfirm = false },
                )
            },
        )
    }
}

@Composable
private fun EnableAllSection(inProgress: Boolean, enabledCount: Int, totalCount: Int, onClick: () -> Unit) {
    val colorScheme = LocalHAColorScheme.current
    Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
        SectionHeader(text = stringResource(commonR.string.health_connect_enable_all_title))
        HASettingsCard(
            modifier = Modifier
                .clip(RoundedCornerShape(HARadius.XL))
                .clickable(role = Role.Button, enabled = !inProgress, onClick = onClick),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(HADimens.SPACE1),
                ) {
                    Text(
                        text = stringResource(commonR.string.health_connect_enable_all_button),
                        style = HATextStyle.Body,
                        textAlign = TextAlign.Start,
                        color = colorScheme.colorTextPrimary,
                    )
                    Text(
                        text = stringResource(
                            commonR.string.health_connect_enable_all_status,
                            enabledCount,
                            totalCount,
                        ),
                        style = HATextStyle.BodyMedium,
                        textAlign = TextAlign.Start,
                        color = colorScheme.colorTextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun RealtimeSyncSection(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
        SectionHeader(text = stringResource(commonR.string.health_connect_realtime_sync_title))
        SwitchRow(
            title = stringResource(commonR.string.health_connect_realtime_sync_title),
            summary = stringResource(commonR.string.health_connect_realtime_sync_summary),
            checked = enabled,
            onToggle = onToggle,
        )
    }
}

@Composable
private fun SwitchRow(title: String, summary: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    val colorScheme = LocalHAColorScheme.current
    HASettingsCard(
        modifier = Modifier
            .clip(RoundedCornerShape(HARadius.XL))
            .clickable(role = Role.Switch) { onToggle(!checked) },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HADimens.SPACE1)) {
                Text(
                    text = title,
                    style = HATextStyle.Body,
                    textAlign = TextAlign.Start,
                    color = colorScheme.colorTextPrimary,
                )
                Text(
                    text = summary,
                    style = HATextStyle.BodyMedium,
                    textAlign = TextAlign.Start,
                    color = colorScheme.colorTextSecondary,
                )
            }
            HASwitch(checked = checked, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    val colorScheme = LocalHAColorScheme.current
    Text(
        text = text,
        style = HATextStyle.BodyMedium,
        color = colorScheme.colorTextSecondary,
        modifier = modifier,
    )
}
