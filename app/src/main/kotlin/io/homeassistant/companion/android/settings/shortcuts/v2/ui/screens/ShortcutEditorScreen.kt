package io.homeassistant.companion.android.settings.shortcuts.v2.ui.screens

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutDraft
import io.homeassistant.companion.android.settings.shortcuts.v2.DynamicShortcutEditorUiState
import io.homeassistant.companion.android.settings.shortcuts.v2.PinnedShortcutEditorUiState
import io.homeassistant.companion.android.settings.shortcuts.v2.ui.components.DynamicShortcutEditor
import io.homeassistant.companion.android.settings.shortcuts.v2.ui.components.PinnedShortcutEditor
import io.homeassistant.companion.android.util.icondialog.IconDialog
import io.homeassistant.companion.android.util.plus
import io.homeassistant.companion.android.util.safeBottomPaddingValues

@RequiresApi(Build.VERSION_CODES.N_MR1)
@Composable
internal fun DynamicShortcutEditorScreen(
    state: DynamicShortcutEditorUiState,
    dispatch: (ShortcutEditAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    ShortcutEditorContent(
        screenState = state.screen,
        draftSeed = state.draftSeed,
        dispatch = dispatch,
        modifier = modifier,
    ) { draft, onDraftChange, onIconClick, onSubmit, onDelete ->
        DynamicShortcutEditor(
            draft = draft,
            state = state,
            onDraftChange = onDraftChange,
            onIconClick = onIconClick,
            onSubmit = onSubmit,
            onDelete = onDelete,
        )
    }
}

@RequiresApi(Build.VERSION_CODES.N_MR1)
@Composable
internal fun PinnedShortcutEditorScreen(
    state: PinnedShortcutEditorUiState,
    dispatch: (ShortcutEditAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    ShortcutEditorContent(
        screenState = state.screen,
        draftSeed = state.draftSeed,
        dispatch = dispatch,
        modifier = modifier,
    ) { draft, onDraftChange, onIconClick, onSubmit, onDelete ->
        PinnedShortcutEditor(
            draft = draft,
            state = state,
            onDraftChange = onDraftChange,
            onIconClick = onIconClick,
            onSubmit = onSubmit,
            onDelete = onDelete,
        )
    }
}

@Composable
private fun ShortcutEditorContent(
    screenState: ShortcutEditorScreenState,
    draftSeed: ShortcutDraft,
    dispatch: (ShortcutEditAction) -> Unit,
    modifier: Modifier = Modifier,
    editor: @Composable (
        draft: ShortcutDraft,
        onDraftChange: (ShortcutDraft) -> Unit,
        onIconClick: () -> Unit,
        onSubmit: () -> Unit,
        onDelete: () -> Unit,
    ) -> Unit,
) {
    if (screenState.isLoading) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            HALoading()
        }
        return
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(PaddingValues(all = HADimens.SPACE4) + safeBottomPaddingValues(applyHorizontal = false)),
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
    ) {
        var draft by rememberSaveable(
            draftSeed.id,
            draftSeed.serverId,
            stateSaver = ShortcutDraftSaver,
        ) {
            mutableStateOf(draftSeed)
        }
        var showIconDialog by rememberSaveable { mutableStateOf(false) }
        val updateDraft: (ShortcutDraft) -> Unit = { updated ->
            draft = updated.copy(isDirty = true)
        }

        if (showIconDialog) {
            IconDialog(
                onSelect = {
                    updateDraft(draft.copy(selectedIcon = it))
                    showIconDialog = false
                },
                onDismissRequest = { showIconDialog = false },
            )
        }
        if (screenState.servers.isEmpty()) {
            Text(
                text = stringResource(R.string.shortcut_no_servers),
                style = HATextStyle.Body,
                color = LocalHAColorScheme.current.colorTextSecondary,
                textAlign = TextAlign.Start,
            )
        }
        editor(
            draft,
            updateDraft,
            { showIconDialog = true },
            { dispatch(ShortcutEditAction.Submit(draft)) },
            { dispatch(ShortcutEditAction.Delete(draft.id)) },
        )
    }
}
