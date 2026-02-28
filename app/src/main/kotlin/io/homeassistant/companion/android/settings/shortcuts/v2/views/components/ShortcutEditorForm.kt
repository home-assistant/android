package io.homeassistant.companion.android.settings.shortcuts.v2.views.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.compose.composable.ButtonVariant
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.composable.HARadioGroup
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.composable.RadioOption
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutDraft
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutTargetValue
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutType
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.toShortcutType
import io.homeassistant.companion.android.settings.shortcuts.v2.views.preview.ShortcutPreviewData
import io.homeassistant.companion.android.settings.shortcuts.v2.views.screens.ShortcutEditorScreenState
import io.homeassistant.companion.android.util.compose.ServerExposedDropdownMenu
import io.homeassistant.companion.android.util.compose.entity.EntityPicker

@Composable
internal fun ShortcutEditorForm(
    draft: ShortcutDraft,
    labelText: String,
    descriptionText: String,
    screen: ShortcutEditorScreenState,
    onDraftChange: (ShortcutDraft) -> Unit,
    isEditing: Boolean,
    canSubmit: Boolean,
    isSaving: Boolean,
    onSubmit: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4)) {
        ShortcutMetadataFields(
            draft = draft,
            labelText = labelText,
            descriptionText = descriptionText,
            screen = screen,
            onLabelChange = { onDraftChange(draft.copy(label = it)) },
            onDescriptionChange = { onDraftChange(draft.copy(description = it)) },
            onServerChange = { onDraftChange(draft.copy(serverId = it)) },
        )

        ShortcutTypeSelector(
            type = draft.target.toShortcutType(),
            onTypeChange = { onDraftChange(draft.withType(it)) },
        )

        ShortcutTargetInput(
            target = draft.target,
            screen = screen,
            serverId = draft.serverId,
            onTargetChange = { onDraftChange(draft.copy(target = it)) },
        )

        PrimaryActionButtons(
            isEditing = isEditing,
            canSubmit = canSubmit,
            isSaving = isSaving,
            onSubmit = onSubmit,
            onDelete = onDelete,
        )
    }
}

private fun ShortcutDraft.withType(type: ShortcutType): ShortcutDraft {
    val newTarget = when (type) {
        ShortcutType.LOVELACE -> {
            target as? ShortcutTargetValue.Lovelace ?: ShortcutTargetValue.Lovelace("")
        }

        ShortcutType.ENTITY_ID -> {
            target as? ShortcutTargetValue.Entity ?: ShortcutTargetValue.Entity("")
        }
    }
    return copy(target = newTarget)
}

@Composable
private fun ShortcutMetadataFields(
    draft: ShortcutDraft,
    labelText: String,
    descriptionText: String,
    screen: ShortcutEditorScreenState,
    onLabelChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onServerChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
        HATextField(
            value = draft.label,
            onValueChange = onLabelChange,
            label = { Text(labelText) },
            modifier = Modifier.fillMaxWidth(),
        )

        HATextField(
            value = draft.description,
            onValueChange = onDescriptionChange,
            label = { Text(descriptionText) },
            supportingText = {
                Text(stringResource(R.string.shortcut_v2_description_support))
            },
            modifier = Modifier.fillMaxWidth(),
        )

        if (screen.servers.isNotEmpty() &&
            (screen.servers.size > 1 || screen.servers.none { it.id == draft.serverId })
        ) {
            ServerExposedDropdownMenu(
                servers = screen.servers,
                current = draft.serverId,
                onSelected = onServerChange,
            )
        }
    }
}

@Composable
private fun ShortcutTypeSelector(type: ShortcutType, onTypeChange: (ShortcutType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
        Text(
            text = stringResource(R.string.shortcut_v2_target_type),
            style = HATextStyle.Body.copy(textAlign = TextAlign.Start),
        )

        HARadioGroup(
            spaceBy = HADimens.SPACE3,
            options = listOf(
                RadioOption(
                    selectionKey = ShortcutType.LOVELACE,
                    headline = stringResource(R.string.shortcut_v2_target_open_dashboard),
                ),
                RadioOption(
                    selectionKey = ShortcutType.ENTITY_ID,
                    headline = stringResource(R.string.shortcut_v2_target_open_entity),
                ),
            ),
            selectionKey = type,
            onSelect = { selected -> onTypeChange(selected.selectionKey) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShortcutTargetInput(
    target: ShortcutTargetValue,
    screen: ShortcutEditorScreenState,
    serverId: Int,
    onTargetChange: (ShortcutTargetValue) -> Unit,
) {
    when (target) {
        is ShortcutTargetValue.Lovelace -> {
            val bringIntoViewRequester = remember { BringIntoViewRequester() }

            HATextField(
                value = target.path,
                onValueChange = { onTargetChange(ShortcutTargetValue.Lovelace(it)) },
                label = { Text(stringResource(R.string.shortcut_v2_dashboard_path_label)) },
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Uri,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(bringIntoViewRequester),
            )
        }

        is ShortcutTargetValue.Entity -> {
            val selectedEntityId = target.entityId.takeIf { it.isNotBlank() }
            val entities = screen.entities[serverId] ?: emptyList()
            val entityRegistry = screen.entityRegistry[serverId]
            val deviceRegistry = screen.deviceRegistry[serverId]
            val areaRegistry = screen.areaRegistry[serverId]
            EntityPicker(
                entities = entities,
                entityRegistry = entityRegistry,
                deviceRegistry = deviceRegistry,
                areaRegistry = areaRegistry,
                selectedEntityId = selectedEntityId,
                onEntitySelectedId = { entityId ->
                    onTargetChange(ShortcutTargetValue.Entity(entityId))
                },
                onEntityCleared = {
                    onTargetChange(ShortcutTargetValue.Entity(""))
                },
            )
        }
    }
}

@Composable
private fun PrimaryActionButtons(
    isEditing: Boolean,
    canSubmit: Boolean,
    isSaving: Boolean,
    onSubmit: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val submitLabelRes = if (isEditing) R.string.update else R.string.add_shortcut

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
        ) {
            if (isEditing && onDelete != null) {
                HAFilledButton(
                    text = stringResource(R.string.delete),
                    onClick = onDelete,
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f),
                    variant = ButtonVariant.DANGER,
                )
            }

            HAFilledButton(
                text = stringResource(submitLabelRes),
                onClick = onSubmit,
                enabled = canSubmit && !isSaving,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Preview(name = "Shortcut Editor Form")
@Composable
private fun ShortcutEditorFormPreview() {
    HAThemeForPreview {
        ShortcutEditorForm(
            draft = ShortcutPreviewData.buildDraft(),
            labelText = "Label",
            descriptionText = "Description",
            screen = ShortcutPreviewData.buildScreenState(servers = ShortcutPreviewData.previewServers),
            onDraftChange = {},
            isEditing = true,
            canSubmit = true,
            isSaving = false,
            onSubmit = {},
            onDelete = {},
        )
    }
}
