package io.homeassistant.companion.android.settings.assist

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import io.homeassistant.companion.android.assist.wakeword.MicroWakeWordModelConfig
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.composable.HAHint
import io.homeassistant.companion.android.common.compose.composable.HALabel
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.composable.HASettingsCard
import io.homeassistant.companion.android.common.compose.composable.HASwitch
import io.homeassistant.companion.android.common.compose.composable.LabelVariant
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.util.plus
import io.homeassistant.companion.android.util.safeBottomPaddingValues
import kotlinx.coroutines.launch
import org.jetbrains.annotations.VisibleForTesting

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun rememberRecordAudioPermissionState(
    snackbarHostState: SnackbarHostState,
    onGranted: () -> Unit,
): PermissionState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val message = stringResource(commonR.string.assist_permission_microphone_required)
    val actionLabel = stringResource(commonR.string.open_settings)
    return rememberPermissionState(Manifest.permission.RECORD_AUDIO) { granted ->
        if (granted) {
            onGranted()
        } else {
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = actionLabel,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AssistSettingsScreen(viewModel: AssistSettingsViewModel, modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val togglePermissionState = rememberRecordAudioPermissionState(snackbarHostState) {
        viewModel.onToggleWakeWord(true)
    }

    val roleRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.refreshDefaultAssistantStatus()
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .padding(safeBottomPaddingValues(applyHorizontal = false)),
            )
        },
        contentWindowInsets = WindowInsets(),
    ) { contentPadding ->
        AssistSettingsContent(
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
            onStartTestWakeWord = { viewModel.setTestingWakeWord(true) },
            onStopTestWakeWord = { viewModel.setTestingWakeWord(false) },
            modifier = Modifier.padding(contentPadding),
        )
    }
}

@Composable
@VisibleForTesting
internal fun AssistSettingsContent(
    uiState: AssistSettingsUiState,
    hasAudioPermission: Boolean,
    onSetDefaultAssistant: () -> Unit,
    onToggleWakeWord: (Boolean) -> Unit,
    onSelectWakeWord: (MicroWakeWordModelConfig) -> Unit,
    onStartTestWakeWord: () -> Unit,
    onStopTestWakeWord: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(all = HADimens.SPACE4) + safeBottomPaddingValues(applyHorizontal = false)),
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
    ) {
        if (uiState.isLoading) {
            HALoading()
        } else {
            // Default Assistant Section
            SectionHeader(
                text = stringResource(commonR.string.assist_default_assistant_title),
                modifier = Modifier.padding(bottom = HADimens.SPACE1),
            )
            DefaultAssistantCard(
                isDefault = uiState.isDefaultAssistant,
                onSetDefault = onSetDefaultAssistant,
            )

            Spacer(modifier = Modifier.height(HADimens.SPACE2))

            // Wake Word Section
            Row(
                horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = HADimens.SPACE1),
            ) {
                SectionHeader(text = stringResource(commonR.string.assist_wake_word_title))
                HALabel(stringResource(commonR.string.experimental), variant = LabelVariant.WARNING)
            }

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
        modifier = modifier,
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

    HASettingsCard {
        Column(
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
}

@Composable
private fun WakeWordEnableRow(enabled: Boolean, canEnable: Boolean, onToggle: (Boolean) -> Unit) {
    val colorScheme = LocalHAColorScheme.current

    HASettingsCard(
        modifier = Modifier.clickable { onToggle(!enabled) }.takeIf { canEnable } ?: Modifier,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(commonR.string.assist_wake_word_enable),
                style = HATextStyle.Body,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start,
                color = if (canEnable) colorScheme.colorTextPrimary else colorScheme.colorTextDisabled,
            )
            HASwitch(
                checked = enabled && canEnable,
                onCheckedChange = onToggle,
                enabled = canEnable,
            )
        }
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

    HASettingsCard(modifier = Modifier.clickable { expanded = true }) {
        Column {
            Text(
                text = stringResource(commonR.string.assist_wake_word_model),
                style = HATextStyle.Body,
                color = colorScheme.colorTextPrimary,
            )
            Text(
                text = selectedModel?.wakeWord ?: "",
                style = HATextStyle.BodyMedium,
                color = colorScheme.colorTextSecondary,
                modifier = Modifier.padding(top = HADimens.SPACE1),
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = colorScheme.colorSurfaceDefault,
            ) {
                availableModels.forEach { model ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = model.wakeWord,
                                style = HATextStyle.BodyMedium,
                            )
                        },
                        onClick = {
                            onSelectModel(model)
                            expanded = false
                        },
                    )
                }
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

    HASettingsCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
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
}

@Preview
@Composable
private fun AssistSettingsContentPreview() {
    HAThemeForPreview {
        AssistSettingsContent(
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
private fun AssistSettingsContentNotDefaultPreview() {
    HAThemeForPreview {
        AssistSettingsContent(
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
