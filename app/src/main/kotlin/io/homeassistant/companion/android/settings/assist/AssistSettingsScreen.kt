package io.homeassistant.companion.android.settings.assist

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import io.homeassistant.companion.android.assist.wakeword.MicroWakeWordModelConfig
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.composable.HAHint
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.composable.HASwitch
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import org.jetbrains.annotations.VisibleForTesting

/* List of settings
- Set Assist as default assistant
- Enable/Disable wake word
    - select wake word
    - test wake word
    - select accuracy // TODO: Allow user to set sensibility https://github.com/esphome/home-assistant-voice-pe/blob/a379b8c5c1a35eeebc8f9925c19aab68743517a4/home-assistant-voice.yaml#L1775
- Select default server
- Select default pipeline
- Setup Assist Satellite
- Show on lock screen?
- Confirmation sound?
- Start from BLE device?
// TODO enable/disable wake word from HA with a command
- ...
 */

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AssistSettingsScreen(viewModel: AssistSettingsViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val togglePermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO) { granted ->
        if (granted) {
            viewModel.onToggleWakeWord(true)
        }
    }

    val testPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO) { granted ->
        if (granted) {
            viewModel.startTestWakeWord()
        }
    }

    val roleRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.refreshDefaultAssistantStatus()
    }

    AssistSettingsScreen(
        uiState = uiState,
        hasAudioPermission = togglePermissionState.status.isGranted,
        onSetDefaultAssistant = {
            roleRequestLauncher.launch(viewModel.getSetDefaultAssistantIntent())
        },
        onToggleWakeWord = { enabled ->
            if (enabled && !togglePermissionState.status.isGranted) {
                togglePermissionState.launchPermissionRequest()
            } else {
                viewModel.onToggleWakeWord(enabled)
            }
        },
        onSelectWakeWord = viewModel::onSelectWakeWordModel,
        onStartTestWakeWord = {
            if (testPermissionState.status.isGranted) {
                viewModel.startTestWakeWord()
            } else {
                testPermissionState.launchPermissionRequest()
            }
        },
        onStopTestWakeWord = viewModel::stopTestWakeWord,
    )
}

@Composable
@VisibleForTesting
internal fun AssistSettingsScreen(
    uiState: AssistSettingsUiState,
    hasAudioPermission: Boolean,
    onSetDefaultAssistant: () -> Unit,
    onToggleWakeWord: (Boolean) -> Unit,
    onSelectWakeWord: (MicroWakeWordModelConfig) -> Unit,
    onStartTestWakeWord: () -> Unit,
    onStopTestWakeWord: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(HADimens.SPACE4),
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
    ) {
        if (uiState.isLoading) {
            HALoading()
        } else {
            // Default Assistant Section
            SectionHeader(text = stringResource(commonR.string.assist_default_assistant_title))
            DefaultAssistantCard(
                isDefault = uiState.isDefaultAssistant,
                onSetDefault = onSetDefaultAssistant,
            )

            Spacer(modifier = Modifier.height(HADimens.SPACE2))

            // Wake Word Section
            SectionHeader(text = stringResource(commonR.string.assist_wake_word_title))
            WakeWordSection(
                uiState = uiState,
                hasAudioPermission = hasAudioPermission,
                onToggleWakeWord = onToggleWakeWord,
                onSelectWakeWord = onSelectWakeWord,
                onStartTestWakeWord = onStartTestWakeWord,
                onStopTestWakeWord = onStopTestWakeWord,
            )
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
        modifier = modifier.padding(bottom = HADimens.SPACE1),
    )
}

@Composable
private fun ColumnScope.WakeWordSection(
    uiState: AssistSettingsUiState,
    hasAudioPermission: Boolean,
    onToggleWakeWord: (Boolean) -> Unit,
    onSelectWakeWord: (MicroWakeWordModelConfig) -> Unit,
    onStartTestWakeWord: () -> Unit,
    onStopTestWakeWord: () -> Unit,
) {
    val isWakeWordEnabled = uiState.isWakeWordEnabled && hasAudioPermission
    WakeWordEnableRow(
        enabled = isWakeWordEnabled,
        canEnable = uiState.isDefaultAssistant,
        onToggle = onToggleWakeWord,
    )

    AnimatedVisibility(visible = isWakeWordEnabled) {
        Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4)) {
            WakeWordModelSelector(
                selectedModel = uiState.selectedWakeWordModel,
                availableModels = uiState.availableModels,
                onSelectModel = onSelectWakeWord,
            )

            HAHint(
                text = stringResource(commonR.string.assist_wake_word_battery_warning),
                modifier = Modifier.fillMaxWidth(),
            )

            WakeWordTestSection(
                wakeWordName = uiState.selectedWakeWordModel?.wakeWord,
                isTesting = uiState.isTestingWakeWord,
                detected = uiState.wakeWordDetected,
                onStartTest = onStartTestWakeWord,
                onStopTest = onStopTestWakeWord,
            )
        }
    }
}

@Composable
private fun DefaultAssistantCard(isDefault: Boolean, onSetDefault: () -> Unit) {
    val colorScheme = LocalHAColorScheme.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(HARadius.XL))
            .background(colorScheme.colorSurfaceLow)
            .padding(HADimens.SPACE4),
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE3),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
        ) {
            if (isDefault) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = colorScheme.colorOnSuccessNormal,
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                text = stringResource(
                    if (isDefault) {
                        commonR.string.assist_default_assistant_enabled
                    } else {
                        commonR.string.assist_default_assistant_disabled
                    },
                ),
                style = HATextStyle.Body,
                color = colorScheme.colorTextPrimary,
            )
        }

        if (!isDefault) {
            HAFilledButton(
                text = stringResource(commonR.string.assist_set_default),
                onClick = onSetDefault,
            )
        }
    }
}

@Composable
private fun WakeWordEnableRow(enabled: Boolean, canEnable: Boolean, onToggle: (Boolean) -> Unit) {
    val colorScheme = LocalHAColorScheme.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(HARadius.XL))
            .background(colorScheme.colorSurfaceLow)
            .then(
                if (canEnable) {
                    Modifier.clickable { onToggle(!enabled) }
                } else {
                    Modifier
                },
            )
            .padding(HADimens.SPACE4),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(commonR.string.assist_wake_word_enable),
            style = HATextStyle.Body,
            color = if (canEnable) colorScheme.colorTextPrimary else colorScheme.colorTextDisabled,
        )
        HASwitch(
            checked = enabled && canEnable,
            onCheckedChange = onToggle,
            enabled = canEnable,
        )
    }
}

@Composable
private fun WakeWordModelSelector(
    selectedModel: MicroWakeWordModelConfig?,
    availableModels: List<MicroWakeWordModelConfig>,
    onSelectModel: (MicroWakeWordModelConfig) -> Unit,
) {
    val colorScheme = LocalHAColorScheme.current
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(HARadius.XL))
            .background(colorScheme.colorSurfaceLow)
            .clickable { expanded = true }
            .padding(HADimens.SPACE4),
    ) {
        Text(
            text = stringResource(commonR.string.assist_wake_word_model),
            style = HATextStyle.BodyMedium,
            color = colorScheme.colorTextSecondary,
        )
        Text(
            text = selectedModel?.wakeWord ?: "",
            style = HATextStyle.Body,
            color = colorScheme.colorTextPrimary,
            modifier = Modifier.padding(top = HADimens.SPACE1),
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            availableModels.forEach { model ->
                DropdownMenuItem(
                    text = { Text(model.wakeWord) },
                    onClick = {
                        onSelectModel(model)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun WakeWordTestSection(
    wakeWordName: String?,
    isTesting: Boolean,
    detected: Boolean,
    onStartTest: () -> Unit,
    onStopTest: () -> Unit,
) {
    val colorScheme = LocalHAColorScheme.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(HARadius.XL))
            .background(colorScheme.colorSurfaceLow)
            .padding(HADimens.SPACE4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE3),
    ) {
        HAFilledButton(
            text = stringResource(
                if (isTesting) commonR.string.assist_wake_word_stop_test else commonR.string.assist_wake_word_test,
            ),
            onClick = if (isTesting) onStopTest else onStartTest,
            prefix = {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            },
        )

        if (isTesting && wakeWordName != null) {
            Text(
                text = stringResource(commonR.string.assist_wake_word_test_hint, wakeWordName),
                style = HATextStyle.BodyMedium,
                color = colorScheme.colorTextSecondary,
            )
        }

        AnimatedVisibility(
            visible = detected,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = colorScheme.colorOnSuccessNormal,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = stringResource(commonR.string.assist_wake_word_detected),
                    style = HATextStyle.Body,
                    color = colorScheme.colorOnSuccessNormal,
                )
            }
        }
    }
}

@Preview
@Composable
private fun AssistSettingsScreenPreview() {
    HAThemeForPreview {
        AssistSettingsScreen(
            uiState = AssistSettingsUiState(
                isLoading = false,
                isDefaultAssistant = true,
                isWakeWordEnabled = true,
                selectedWakeWordModel = null,
                availableModels = emptyList(),
                isTestingWakeWord = true,
                wakeWordDetected = false,
            ),
            hasAudioPermission = true,
            onSetDefaultAssistant = {},
            onToggleWakeWord = {},
            onSelectWakeWord = {},
            onStartTestWakeWord = {},
            onStopTestWakeWord = {},
        )
    }
}

@Preview
@Composable
private fun AssistSettingsScreenNotDefaultPreview() {
    HAThemeForPreview {
        AssistSettingsScreen(
            uiState = AssistSettingsUiState(
                isLoading = false,
                isDefaultAssistant = false,
                isWakeWordEnabled = false,
                selectedWakeWordModel = null,
                availableModels = emptyList(),
            ),
            hasAudioPermission = false,
            onSetDefaultAssistant = {},
            onToggleWakeWord = {},
            onSelectWakeWord = {},
            onStartTestWakeWord = {},
            onStopTestWakeWord = {},
        )
    }
}
