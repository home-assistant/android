package io.homeassistant.companion.android.settings.shortcuts.v2.ui.components

import android.os.Build
import androidx.annotation.RequiresApi
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
import io.homeassistant.companion.android.settings.shortcuts.v2.ShortcutEditorUiState
import io.homeassistant.companion.android.settings.shortcuts.v2.ui.preview.ShortcutPreviewData
import io.homeassistant.companion.android.settings.shortcuts.v2.ui.screens.ShortcutEditorScreenState
import io.homeassistant.companion.android.settings.shortcuts.v2.ui.selector.ShortcutIconPicker

@RequiresApi(Build.VERSION_CODES.N_MR1)
@Composable
internal fun PinnedShortcutEditor(
    draft: ShortcutDraft,
    state: ShortcutEditorUiState.EditorState.Pinned,
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
                text = stringResource(R.string.shortcut_pinned),
                style = HATextStyle.HeadlineMedium,
                color = LocalHAColorScheme.current.colorFillPrimaryLoudResting,
                textAlign = TextAlign.Start,
                modifier = Modifier.weight(1f),
            )

            ShortcutIconPicker(
                selectedIcon = draft.selectedIcon,
                onIconClick = onIconClick,
            )
        }

        ShortcutEditorForm(
            draft = draft,
            labelText = stringResource(R.string.shortcut_pinned_label),
            descriptionText = stringResource(R.string.shortcut_pinned_desc),
            screen = screen,
            onDraftChange = onDraftChange,
            isCreated = state.isCreated,
            canSubmit = canSubmit,
            onSubmit = onSubmit,
            onDelete = onDelete,
        )
    }
}

@RequiresApi(Build.VERSION_CODES.N_MR1)
@Preview(name = "Pinned Shortcut Editor")
@Composable
private fun PinnedShortcutEditorPreview() {
    HAThemeForPreview {
        PinnedShortcutEditor(
            draft = ShortcutPreviewData.buildPinnedDraft(),
            state = ShortcutPreviewData.buildPinnedEditorState(),
            screen = ShortcutPreviewData.buildScreenState(),
            onDraftChange = {},
            onIconClick = {},
            onSubmit = {},
            onDelete = {},
        )
    }
}
