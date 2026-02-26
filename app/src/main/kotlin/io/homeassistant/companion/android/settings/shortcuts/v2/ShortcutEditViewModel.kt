package io.homeassistant.companion.android.settings.shortcuts.v2

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.shortcuts.ShortcutsRepository
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.DynamicEditorData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.PinResult
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.PinnedEditorData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutDraft
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutEditorData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutError
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutResult
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.empty
import io.homeassistant.companion.android.settings.shortcuts.v2.views.screens.ShortcutEditAction
import io.homeassistant.companion.android.settings.shortcuts.v2.views.screens.ShortcutEditorScreenState
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class ShortcutEditorUiState(
    val screen: ShortcutEditorScreenState = ShortcutEditorScreenState(isLoading = true),
    val editor: EditorState = EditorState.DynamicCreate.initial(0),
) {
    @Immutable
    sealed interface EditorState {
        val draftSeed: ShortcutDraft

        @Immutable
        sealed interface Pinned : EditorState

        @Immutable
        data class PinnedCreate(override val draftSeed: ShortcutDraft) : Pinned {
            companion object {
                fun initial(id: String) = PinnedCreate(
                    draftSeed = ShortcutDraft.empty(id),
                )
            }
        }

        @Immutable
        data class PinnedEdit(override val draftSeed: ShortcutDraft) : Pinned

        @Immutable
        sealed interface Dynamic : EditorState {
            val index: Int
        }

        @Immutable
        data class DynamicCreate(override val index: Int, override val draftSeed: ShortcutDraft) : Dynamic {
            companion object {
                fun initial(index: Int) = DynamicCreate(
                    index = index,
                    draftSeed = ShortcutDraft.empty(index),
                )
            }
        }

        @Immutable
        data class DynamicEdit(override val index: Int, override val draftSeed: ShortcutDraft) : Dynamic
    }
}

@HiltViewModel
class ShortcutEditViewModel @Inject constructor(private val shortcutsRepository: ShortcutsRepository) :
    ViewModel() {

    private val _uiState = MutableStateFlow(ShortcutEditorUiState())
    private val _pinResultEvents = MutableSharedFlow<PinResult>(extraBufferCapacity = 1)
    private val _closeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val uiState: StateFlow<ShortcutEditorUiState> = _uiState.asStateFlow()
    val pinResultEvents = _pinResultEvents.asSharedFlow()
    val closeEvents = _closeEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            loadData()
        }
    }

    private suspend fun loadData() {
        when (val result = shortcutsRepository.loadEditorData()) {
            is ShortcutResult.Success -> applyEditorData(result.data)
            is ShortcutResult.Error -> setScreenError(result.error)
        }
        updateScreen { it.copy(isLoading = false) }
    }

    fun dispatch(action: ShortcutEditAction) {
        when (_uiState.value.editor) {
            is ShortcutEditorUiState.EditorState.PinnedCreate,
            is ShortcutEditorUiState.EditorState.PinnedEdit,
            -> {
                when (action) {
                    is ShortcutEditAction.Submit -> upsertPinned(action.draft)
                    is ShortcutEditAction.Delete -> deletePinned()
                }
            }

            is ShortcutEditorUiState.EditorState.Dynamic -> {
                when (action) {
                    is ShortcutEditAction.Submit -> upsertDynamic(action.draft)
                    is ShortcutEditAction.Delete -> deleteDynamic()
                }
            }
        }
    }

    fun openCreatePinned() {
        viewModelScope.launch {
            when (val result = shortcutsRepository.loadPinnedEditorForCreate()) {
                is ShortcutResult.Success -> {
                    setScreenError(null)
                    setPinnedEditor(result.data)
                }

                is ShortcutResult.Error -> {
                    setScreenError(result.error)
                }
            }
        }
    }

    fun editPinned(shortcutId: String) {
        viewModelScope.launch {
            when (val result = shortcutsRepository.loadPinnedEditor(shortcutId)) {
                is ShortcutResult.Success -> {
                    setScreenError(null)
                    setPinnedEditor(result.data)
                }

                is ShortcutResult.Error -> {
                    setScreenError(result.error)
                }
            }
        }
    }

    fun openDynamic(index: Int) {
        viewModelScope.launch {
            when (val result = shortcutsRepository.loadDynamicEditor(index)) {
                is ShortcutResult.Success -> {
                    setScreenError(null)
                    setDynamicEditor(result.data)
                }

                is ShortcutResult.Error -> {
                    handleDynamicError(result.error)
                }
            }
        }
    }

    fun createDynamicFirstAvailable() {
        viewModelScope.launch {
            when (val result = shortcutsRepository.loadDynamicEditorFirstAvailable()) {
                is ShortcutResult.Success -> {
                    setScreenError(null)
                    setDynamicEditor(result.data)
                }

                is ShortcutResult.Error -> {
                    handleDynamicError(result.error)
                }
            }
        }
    }

    private fun upsertPinned(draft: ShortcutDraft) {
        viewModelScope.launch {
            val isEditing = _uiState.value.editor is ShortcutEditorUiState.EditorState.PinnedEdit
            when (val result = shortcutsRepository.upsertPinnedShortcut(draft)) {
                is ShortcutResult.Success -> {
                    setScreenError(null)
                    _pinResultEvents.emit(result.data)
                    if (!isEditing) {
                        _closeEvents.emit(Unit)
                    }
                }

                is ShortcutResult.Error -> {
                    setScreenError(result.error)
                }
            }
        }
    }

    private fun deletePinned() {
        val editor = _uiState.value.editor as? ShortcutEditorUiState.EditorState.PinnedEdit ?: return
        viewModelScope.launch {
            when (val result = shortcutsRepository.deletePinnedShortcut(editor.draftSeed.id)) {
                is ShortcutResult.Success -> _closeEvents.emit(Unit)
                is ShortcutResult.Error -> setScreenError(result.error)
            }
        }
    }

    private fun upsertDynamic(draft: ShortcutDraft) {
        val editor = _uiState.value.editor as? ShortcutEditorUiState.EditorState.Dynamic ?: return
        viewModelScope.launch {
            when (
                val result = shortcutsRepository.upsertDynamicShortcut(
                    editor.index,
                    draft,
                    editor is ShortcutEditorUiState.EditorState.DynamicEdit,
                )
            ) {
                is ShortcutResult.Success -> {
                    setScreenError(null)
                    setDynamicEditor(result.data)
                }

                is ShortcutResult.Error -> {
                    handleDynamicError(result.error)
                }
            }
        }
    }

    private fun deleteDynamic() {
        val editor = _uiState.value.editor as? ShortcutEditorUiState.EditorState.DynamicEdit ?: return
        viewModelScope.launch {
            when (val result = shortcutsRepository.deleteDynamicShortcut(editor.index)) {
                is ShortcutResult.Success -> _closeEvents.emit(Unit)
                is ShortcutResult.Error -> setScreenError(result.error)
            }
        }
    }

    private fun updateScreen(updater: (ShortcutEditorScreenState) -> ShortcutEditorScreenState) {
        _uiState.update { state ->
            state.copy(screen = updater(state.screen))
        }
    }

    private fun applyEditorData(data: ShortcutEditorData) {
        updateScreen { state ->
            state.copy(
                error = null,
                servers = data.servers.toList(),
                entities = data.serverDataById.mapValues { it.value.entities.toList() },
                entityRegistry = data.serverDataById.mapValues { it.value.entityRegistry.toList() },
                deviceRegistry = data.serverDataById.mapValues { it.value.deviceRegistry.toList() },
                areaRegistry = data.serverDataById.mapValues { it.value.areaRegistry.toList() },
            )
        }
    }

    private fun setScreenError(error: ShortcutError?) {
        updateScreen { state ->
            if (state.error == error) state else state.copy(error = error)
        }
    }

    private fun handleDynamicError(error: ShortcutError) {
        setScreenError(error)
    }

    private fun updateEditor(updater: (ShortcutEditorUiState.EditorState) -> ShortcutEditorUiState.EditorState) {
        _uiState.update { state ->
            state.copy(editor = updater(state.editor))
        }
    }

    private fun setDynamicEditor(data: DynamicEditorData) {
        updateEditor {
            when (data) {
                is DynamicEditorData.Create -> ShortcutEditorUiState.EditorState.DynamicCreate(
                    index = data.index,
                    draftSeed = data.draftSeed,
                )

                is DynamicEditorData.Edit -> ShortcutEditorUiState.EditorState.DynamicEdit(
                    index = data.index,
                    draftSeed = data.draftSeed,
                )
            }
        }
    }

    private fun setPinnedEditor(data: PinnedEditorData) {
        updateEditor {
            when (data) {
                is PinnedEditorData.Create -> ShortcutEditorUiState.EditorState.PinnedCreate(
                    draftSeed = data.draftSeed,
                )

                is PinnedEditorData.Edit -> ShortcutEditorUiState.EditorState.PinnedEdit(
                    draftSeed = data.draftSeed,
                )
            }
        }
    }
}
