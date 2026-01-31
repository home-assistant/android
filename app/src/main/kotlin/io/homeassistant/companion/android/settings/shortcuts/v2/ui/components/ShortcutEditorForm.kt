package io.homeassistant.companion.android.settings.shortcuts.v2.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutDraft
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutTargetValue
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutType
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.toShortcutType
import io.homeassistant.companion.android.settings.shortcuts.v2.ui.preview.ShortcutPreviewData
import io.homeassistant.companion.android.settings.shortcuts.v2.ui.screens.ShortcutEditorScreenState
import io.homeassistant.companion.android.settings.shortcuts.v2.ui.selector.ShortcutTargetInput
import io.homeassistant.companion.android.settings.shortcuts.v2.ui.selector.ShortcutTypeSelector

@Composable
internal fun ShortcutEditorForm(
    draft: ShortcutDraft,
    labelText: String,
    descriptionText: String,
    screen: ShortcutEditorScreenState,
    onDraftChange: (ShortcutDraft) -> Unit,
    isEditing: Boolean,
    canSubmit: Boolean,
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
            onSubmit = {},
            onDelete = {},
        )
    }
}
