package io.homeassistant.companion.android.settings.shortcuts.v2.views.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutDraft
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutTargetValue
import io.homeassistant.companion.android.settings.shortcuts.v2.ShortcutEditorUiState
import io.homeassistant.companion.android.settings.shortcuts.v2.views.preview.ShortcutPreviewData
import io.homeassistant.companion.android.settings.shortcuts.v2.views.screens.ShortcutEditorScreenState
import io.homeassistant.companion.android.settings.shortcuts.v2.views.selector.ShortcutIconPicker

@Composable
internal fun DynamicShortcutEditor(
    draft: ShortcutDraft,
    state: ShortcutEditorUiState.EditorState.Dynamic,
    screen: ShortcutEditorScreenState,
    onDraftChange: (ShortcutDraft) -> Unit,
    onIconClick: () -> Unit,
    onSubmit: () -> Unit,
    onDelete: () -> Unit,
) {
    val canSubmit by remember(draft, screen.servers) {
        derivedStateOf {
            canSubmit(draft = draft, screen = screen, requireId = false)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.shortcut_n, state.index + 1),
                style = HATextStyle.HeadlineMedium,
                color = LocalHAColorScheme.current.colorFillPrimaryLoudResting,
                textAlign = TextAlign.Start,
                modifier = Modifier.weight(1f),
            )

            ShortcutIconPicker(
                selectedIconName = draft.selectedIconName,
                onIconClick = onIconClick,
            )
        }

        ShortcutEditorForm(
            draft = draft,
            labelText = stringResource(R.string.shortcut_label_n, state.index + 1),
            descriptionText = stringResource(R.string.shortcut_description_n, state.index + 1),
            screen = screen,
            onDraftChange = onDraftChange,
            isEditing = state is ShortcutEditorUiState.EditorState.DynamicEdit,
            canSubmit = canSubmit,
            onSubmit = onSubmit,
            onDelete = onDelete,
        )
    }
}

internal fun canSubmit(draft: ShortcutDraft, screen: ShortcutEditorScreenState, requireId: Boolean): Boolean {
    val hasValidTarget = when (val target = draft.target) {
        is ShortcutTargetValue.Lovelace -> target.path.isNotBlank()
        is ShortcutTargetValue.Entity -> target.entityId.isNotBlank()
    }
    return (!requireId || draft.id.isNotEmpty()) &&
        draft.label.isNotEmpty() &&
        draft.description.isNotEmpty() &&
        hasValidTarget &&
        screen.servers.any { it.id == draft.serverId }
}

@Preview(name = "Dynamic Shortcut Editor")
@Composable
private fun DynamicShortcutEditorPreview() {
    HAThemeForPreview {
        DynamicShortcutEditor(
            draft = ShortcutPreviewData.buildDraft(),
            state = ShortcutPreviewData.buildDynamicEditorState(),
            screen = ShortcutPreviewData.buildScreenState(),
            onDraftChange = {},
            onIconClick = {},
            onSubmit = {},
            onDelete = {},
        )
    }
}
