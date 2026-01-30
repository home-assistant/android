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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutDraft
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutError
import io.homeassistant.companion.android.settings.shortcuts.v2.ShortcutEditorUiState
import io.homeassistant.companion.android.settings.shortcuts.v2.ui.components.DynamicShortcutEditor
import io.homeassistant.companion.android.settings.shortcuts.v2.ui.components.EmptyStateContent
import io.homeassistant.companion.android.settings.shortcuts.v2.ui.components.EmptyStateContentSlots
import io.homeassistant.companion.android.settings.shortcuts.v2.ui.components.PinnedShortcutEditor
import io.homeassistant.companion.android.util.icondialog.IconDialog
import io.homeassistant.companion.android.util.plus
import io.homeassistant.companion.android.util.safeBottomPaddingValues

@RequiresApi(Build.VERSION_CODES.N_MR1)
@Composable
internal fun ShortcutEditorScreen(
    state: ShortcutEditorUiState,
    dispatch: (ShortcutEditAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val noServers = state.screen.servers.isEmpty()
    when (val editor = state.editor) {
        is ShortcutEditorUiState.EditorState.Dynamic -> {
            when {
                noServers -> EmptyStateContent(hasServers = false)
                state.screen.error == ShortcutError.SlotsFull -> EmptyStateContentSlots()
                else -> ShortcutEditorContent(
                    screenState = state.screen,
                    draftSeed = editor.draftSeed,
                    dispatch = dispatch,
                    modifier = modifier,
                ) { draft, onDraftChange, onIconClick, onSubmit, onDelete ->
                    DynamicShortcutEditor(
                        draft = draft,
                        state = editor,
                        screen = state.screen,
                        onDraftChange = onDraftChange,
                        onIconClick = onIconClick,
                        onSubmit = onSubmit,
                        onDelete = onDelete,
                    )
                }
            }
        }

        is ShortcutEditorUiState.EditorState.Pinned -> {
            when {
                noServers -> EmptyStateContent(hasServers = false)
                else -> {
                    ShortcutEditorContent(
                        screenState = state.screen,
                        draftSeed = editor.draftSeed,
                        dispatch = dispatch,
                        modifier = modifier,
                    ) { draft, onDraftChange, onIconClick, onSubmit, onDelete ->
                        PinnedShortcutEditor(
                            draft = draft,
                            state = editor,
                            screen = state.screen,
                            onDraftChange = onDraftChange,
                            onIconClick = onIconClick,
                            onSubmit = onSubmit,
                            onDelete = onDelete,
                        )
                    }
                }
            }
        }
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
            draft = updated.copy()
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
        editor(
            draft,
            updateDraft,
            { showIconDialog = true },
            { dispatch(ShortcutEditAction.Submit(draft)) },
            { dispatch(ShortcutEditAction.Delete(draft.id)) },
        )
    }
}
