package io.homeassistant.companion.android.settings.shortcuts.v2

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.shortcuts.ShortcutsRepository
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.AppEditorData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.HomeEditorData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.PinResult
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
    val editor: EditorState = EditorState.AppCreate.initial(0),
) {
    @Immutable
    sealed interface EditorState {
        val draftSeed: ShortcutDraft

        @Immutable
        sealed interface Home : EditorState

        @Immutable
        data class HomeCreate(override val draftSeed: ShortcutDraft) : Home {
            companion object {
                fun initial(id: String) = HomeCreate(
                    draftSeed = ShortcutDraft.empty(id),
                )
            }
        }

        @Immutable
        data class HomeEdit(override val draftSeed: ShortcutDraft) : Home

        @Immutable
        sealed interface App : EditorState {
            val index: Int
        }

        @Immutable
        data class AppCreate(override val index: Int, override val draftSeed: ShortcutDraft) : App {
            companion object {
                fun initial(index: Int) = AppCreate(
                    index = index,
                    draftSeed = ShortcutDraft.empty(index),
                )
            }
        }

        @Immutable
        data class AppEdit(override val index: Int, override val draftSeed: ShortcutDraft) : App
    }
}

@HiltViewModel
class EditShortcutViewModel @Inject constructor(private val shortcutsRepository: ShortcutsRepository) :
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
        val screenState = when (val result = shortcutsRepository.loadEditorData()) {
            is ShortcutResult.Success -> buildEditorScreenState(result.data)
            is ShortcutResult.Error -> ShortcutEditorScreenState(isLoading = false, error = result.error)
        }
        updateScreen { screenState }
    }

    fun dispatch(action: ShortcutEditAction) {
        val editor = _uiState.value.editor
        when (editor) {
            is ShortcutEditorUiState.EditorState.Home -> handleHomeAction(action)
            is ShortcutEditorUiState.EditorState.App -> handleAppAction(action)
        }
    }

    private fun handleHomeAction(action: ShortcutEditAction) {
        when (action) {
            is ShortcutEditAction.Submit -> upsertHomeShortcut(action.draft)
            is ShortcutEditAction.Delete -> deleteHomeShortcut()
        }
    }

    private fun handleAppAction(action: ShortcutEditAction) {
        when (action) {
            is ShortcutEditAction.Submit -> upsertAppShortcut(action.draft)
            is ShortcutEditAction.Delete -> deleteAppShortcut()
        }
    }

    fun openCreateHomeShortcut() {
        viewModelScope.launch {
            when (val result = shortcutsRepository.loadHomeEditorForCreate()) {
                is ShortcutResult.Success -> setHomeEditor(result.data)
                is ShortcutResult.Error -> setScreenError(result.error)
            }
        }
    }

    fun openEditHomeShortcut(shortcutId: String) {
        viewModelScope.launch {
            when (val result = shortcutsRepository.loadHomeEditor(shortcutId)) {
                is ShortcutResult.Success -> setHomeEditor(result.data)
                is ShortcutResult.Error -> setScreenError(result.error)
            }
        }
    }

    fun openEditAppShortcut(index: Int) {
        viewModelScope.launch {
            when (val result = shortcutsRepository.loadAppEditor(index)) {
                is ShortcutResult.Success -> setAppEditor(result.data)
                is ShortcutResult.Error -> handleAppError(result.error)
            }
        }
    }

    fun createAppShortcutFirstAvailable() {
        viewModelScope.launch {
            when (val result = shortcutsRepository.loadAppEditorFirstAvailable()) {
                is ShortcutResult.Success -> setAppEditor(result.data)
                is ShortcutResult.Error -> handleAppError(result.error)
            }
        }
    }

    private fun upsertHomeShortcut(draft: ShortcutDraft) {
        viewModelScope.launch {
            setSaving(true)
            when (val result = shortcutsRepository.upsertHomeShortcut(draft)) {
                is ShortcutResult.Success -> {
                    setSaving(false)
                    _pinResultEvents.emit(result.data)
                    _closeEvents.emit(Unit)
                }

                is ShortcutResult.Error -> {
                    setSaving(false)
                    setScreenError(result.error)
                }
            }
        }
    }

    private fun deleteHomeShortcut() {
        val editor = _uiState.value.editor as? ShortcutEditorUiState.EditorState.HomeEdit ?: return
        viewModelScope.launch {
            setSaving(true)
            when (val result = shortcutsRepository.deleteHomeShortcut(editor.draftSeed.id)) {
                is ShortcutResult.Success -> {
                    setSaving(false)
                    _closeEvents.emit(Unit)
                }

                is ShortcutResult.Error -> {
                    setSaving(false)
                    setScreenError(result.error)
                }
            }
        }
    }

    private fun upsertAppShortcut(draft: ShortcutDraft) {
        val editor = _uiState.value.editor as? ShortcutEditorUiState.EditorState.App ?: return
        viewModelScope.launch {
            setSaving(true)
            when (
                val result = shortcutsRepository.upsertAppShortcut(
                    index = editor.index,
                    shortcut = draft,
                    isEditing = editor is ShortcutEditorUiState.EditorState.AppEdit,
                )
            ) {
                is ShortcutResult.Success -> {
                    setSaving(false)
                    _closeEvents.emit(Unit)
                }

                is ShortcutResult.Error -> {
                    setSaving(false)
                    handleAppError(result.error)
                }
            }
        }
    }

    private fun deleteAppShortcut() {
        val editor = _uiState.value.editor as? ShortcutEditorUiState.EditorState.AppEdit ?: return
        viewModelScope.launch {
            setSaving(true)
            when (val result = shortcutsRepository.deleteAppShortcut(editor.index)) {
                is ShortcutResult.Success -> {
                    setSaving(false)
                    _closeEvents.emit(Unit)
                }

                is ShortcutResult.Error -> {
                    setSaving(false)
                    setScreenError(result.error)
                }
            }
        }
    }

    private fun setSaving(isSaving: Boolean) {
        updateScreen { it.copy(isSaving = isSaving) }
    }

    private fun updateScreen(updater: (ShortcutEditorScreenState) -> ShortcutEditorScreenState) {
        _uiState.update { state ->
            state.copy(screen = updater(state.screen))
        }
    }

    private fun buildEditorScreenState(data: ShortcutEditorData): ShortcutEditorScreenState {
        return ShortcutEditorScreenState(
            isLoading = false,
            error = null,
            servers = data.servers.toList(),
            entities = data.serverDataById.mapValues { it.value.entities.toList() },
            entityRegistry = data.serverDataById.mapValues { it.value.entityRegistry.toList() },
            deviceRegistry = data.serverDataById.mapValues { it.value.deviceRegistry.toList() },
            areaRegistry = data.serverDataById.mapValues { it.value.areaRegistry.toList() },
        )
    }

    private fun setScreenError(error: ShortcutError?) {
        updateScreen { state ->
            if (state.error == error) state else state.copy(error = error)
        }
    }

    private fun handleAppError(error: ShortcutError) {
        setScreenError(error)
    }

    private fun updateEditor(updater: (ShortcutEditorUiState.EditorState) -> ShortcutEditorUiState.EditorState) {
        _uiState.update { state ->
            state.copy(editor = updater(state.editor), screen = state.screen.copy(error = null))
        }
    }

    private fun setAppEditor(data: AppEditorData) {
        updateEditor {
            when (data) {
                is AppEditorData.Create -> ShortcutEditorUiState.EditorState.AppCreate(
                    index = data.index,
                    draftSeed = data.draftSeed,
                )

                is AppEditorData.Edit -> ShortcutEditorUiState.EditorState.AppEdit(
                    index = data.index,
                    draftSeed = data.draftSeed,
                )
            }
        }
    }

    private fun setHomeEditor(data: HomeEditorData) {
        updateEditor {
            when (data) {
                is HomeEditorData.Create -> ShortcutEditorUiState.EditorState.HomeCreate(
                    draftSeed = data.draftSeed,
                )

                is HomeEditorData.Edit -> ShortcutEditorUiState.EditorState.HomeEdit(
                    draftSeed = data.draftSeed,
                )
            }
        }
    }
}
