package io.homeassistant.companion.android.settings.shortcuts.v2

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.shortcuts.ShortcutsRepository
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.PinResult
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ServersResult
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutDraft
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.empty
import io.homeassistant.companion.android.settings.shortcuts.v2.ui.screens.ShortcutEditAction
import io.homeassistant.companion.android.settings.shortcuts.v2.ui.screens.ShortcutEditorScreenState
import javax.inject.Inject
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import timber.log.Timber

@Immutable
data class ShortcutEditorUiState(
    val screen: ShortcutEditorScreenState = ShortcutEditorScreenState(isLoading = true),
    val editor: EditorState = EditorState.Dynamic.initial(0),
) {
    @Immutable
    sealed interface EditorState {
        val draftSeed: ShortcutDraft

        @Immutable
        data class Pinned(override val draftSeed: ShortcutDraft, val isCreated: Boolean) : EditorState {
            companion object {
                fun initial(id: String, isCreated: Boolean = false) = Pinned(
                    draftSeed = ShortcutDraft.empty(id),
                    isCreated = isCreated,
                )
            }
        }

        @Immutable
        data class Dynamic(val selectedIndex: Int, override val draftSeed: ShortcutDraft, val isCreated: Boolean) :
            EditorState {
            companion object {
                fun initial(index: Int) = Dynamic(
                    selectedIndex = index,
                    draftSeed = ShortcutDraft.empty(dynamicDraftSeedId(index)),
                    isCreated = false,
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.N_MR1)
@HiltViewModel
class ShortcutEditViewModel @Inject constructor(private val shortcutsRepository: ShortcutsRepository) :
    ViewModel() {

    private val canPinShortcuts = shortcutsRepository.canPinShortcuts
    private val maxDynamicShortcuts = shortcutsRepository.maxDynamicShortcuts

    private val _uiState = MutableStateFlow(ShortcutEditorUiState())
    private val _pinResultEvents = MutableSharedFlow<PinResult>(extraBufferCapacity = 1)
    private val _closeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val uiState: StateFlow<ShortcutEditorUiState> = _uiState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = _uiState.value,
    )
    val pinResultEvents = _pinResultEvents.asSharedFlow()
    val closeEvents = _closeEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            loadData()
        }
    }

    private suspend fun loadData() {
        try {
            val serversResult = shortcutsRepository.getServers()
            val servers = when (serversResult) {
                is ServersResult.Success -> serversResult.servers.toImmutableList()
                ServersResult.NoServers -> persistentListOf()
            }
            updateScreen { it.copy(servers = servers) }
            applyServerIdToSeed(serversResult)

            supervisorScope {
                servers.map { server ->
                    async {
                        runCatching { loadServerData(server.id) }
                            .onFailure {
                                Timber.e(
                                    it,
                                    "Failed to load data for serverId=%s",
                                    server.id,
                                )
                            }
                    }
                }.awaitAll()
            }
        } finally {
            updateScreen { it.copy(isLoading = false) }
        }
    }

    fun dispatch(action: ShortcutEditAction) {
        when (_uiState.value.editor) {
            is ShortcutEditorUiState.EditorState.Pinned -> {
                when (action) {
                    is ShortcutEditAction.Submit -> upsertPinned(action.draft)
                    is ShortcutEditAction.Delete -> deletePinned(action.draftId)
                }
            }

            is ShortcutEditorUiState.EditorState.Dynamic -> {
                when (action) {
                    is ShortcutEditAction.Submit -> createDynamic(action.draft)
                    is ShortcutEditAction.Delete -> deleteDynamic()
                }
            }
        }
    }

    fun openCreatePinned() {
        if (!canPinShortcuts) return
        viewModelScope.launch {
            val serverId = resolveServerId(shortcutsRepository.getServers())
            val draft = buildPinnedDraft(serverId = serverId)
            updateEditor { ShortcutEditorUiState.EditorState.Pinned(draftSeed = draft, isCreated = false) }
        }
    }

    fun editPinned(shortcutId: String) {
        if (!canPinShortcuts) return
        openPinnedEditor(shortcutId)
    }

    fun openDynamic(index: Int) {
        if (index !in 0 until maxDynamicShortcuts) return
        viewModelScope.launch {
            val serverId = resolveServerId(shortcutsRepository.getServers())
            setDynamicEditor(index, serverId)
            refreshDynamicShortcuts(updateSeed = true)
        }
    }

    fun createDynamicFirstAvailable() {
        viewModelScope.launch {
            val dynamicDrafts = shortcutsRepository.loadDynamicShortcuts()
            val index = (0 until maxDynamicShortcuts).firstOrNull { candidate ->
                !dynamicDrafts.containsKey(candidate)
            } ?: return@launch
            val serverId = resolveServerId(shortcutsRepository.getServers())
            setDynamicEditor(index, serverId)
            refreshDynamicShortcuts(updateSeed = true)
        }
    }

    private fun upsertPinned(draft: ShortcutDraft) {
        if (!canPinShortcuts) return
        viewModelScope.launch {
            val isEditing = (_uiState.value.editor as? ShortcutEditorUiState.EditorState.Pinned)?.isCreated == true
            val result = shortcutsRepository.upsertPinnedShortcut(draft)
            _pinResultEvents.emit(result)
            if (!isEditing && result != PinResult.NotSupported) {
                _closeEvents.emit(Unit)
            }
        }
    }

    private fun deletePinned(shortcutId: String) {
        if (!canPinShortcuts) return
        if (shortcutId.isBlank()) return
        viewModelScope.launch {
            shortcutsRepository.deletePinnedShortcut(shortcutId)
            _closeEvents.emit(Unit)
        }
    }

    private fun createDynamic(draft: ShortcutDraft) {
        val editor = _uiState.value.editor as? ShortcutEditorUiState.EditorState.Dynamic ?: return
        viewModelScope.launch {
            shortcutsRepository.upsertDynamicShortcut(editor.selectedIndex, draft)
            refreshDynamicShortcuts(updateSeed = false)
        }
    }

    private fun deleteDynamic() {
        val editor = _uiState.value.editor as? ShortcutEditorUiState.EditorState.Dynamic ?: return
        deleteDynamicShortcut(editor.selectedIndex)
    }

    private fun applyServerIdToSeed(result: ServersResult) {
        val serverId = resolveServerId(result) ?: return
        updateEditor { editor ->
            if (editor.draftSeed.serverId != 0) {
                return@updateEditor editor
            }
            when (editor) {
                is ShortcutEditorUiState.EditorState.Pinned -> {
                    editor.copy(draftSeed = editor.draftSeed.copy(serverId = serverId))
                }

                is ShortcutEditorUiState.EditorState.Dynamic -> {
                    editor.copy(draftSeed = editor.draftSeed.copy(serverId = serverId))
                }
            }
        }
    }

    private suspend fun loadServerData(serverId: Int) {
        val serverData = shortcutsRepository.loadServerData(serverId)

        updateScreen { state ->
            state.copy(
                entities = state.entities.toPersistentMap().put(serverId, serverData.entities.toImmutableList()),
                entityRegistry = state.entityRegistry.toPersistentMap()
                    .put(serverId, serverData.entityRegistry.toImmutableList()),
                deviceRegistry = state.deviceRegistry.toPersistentMap()
                    .put(serverId, serverData.deviceRegistry.toImmutableList()),
                areaRegistry = state.areaRegistry.toPersistentMap()
                    .put(serverId, serverData.areaRegistry.toImmutableList()),
            )
        }
    }

    private fun updateScreen(updater: (ShortcutEditorScreenState) -> ShortcutEditorScreenState) {
        _uiState.update { state ->
            state.copy(screen = updater(state.screen))
        }
    }

    private suspend fun refreshDynamicShortcuts(updateSeed: Boolean) {
        val loadedDraftsByIndex = shortcutsRepository.loadDynamicShortcuts()
        updateDynamic { state ->
            val loadedDraft = loadedDraftsByIndex[state.selectedIndex]
            val fallback = ShortcutDraft.empty(dynamicDraftSeedId(state.selectedIndex))
                .copy(serverId = state.draftSeed.serverId)
            state.copy(
                draftSeed = if (updateSeed) {
                    loadedDraft ?: fallback
                } else {
                    state.draftSeed
                },
                isCreated = loadedDraft != null,
            )
        }
    }

    private fun deleteDynamicShortcut(index: Int) {
        if (index !in 0 until maxDynamicShortcuts) return
        viewModelScope.launch {
            shortcutsRepository.deleteDynamicShortcut(index)
            _closeEvents.emit(Unit)
        }
    }

    private fun updateEditor(updater: (ShortcutEditorUiState.EditorState) -> ShortcutEditorUiState.EditorState) {
        _uiState.update { state ->
            state.copy(editor = updater(state.editor))
        }
    }

    private fun updatePinned(
        updater: (ShortcutEditorUiState.EditorState.Pinned) -> ShortcutEditorUiState.EditorState.Pinned,
    ) {
        updateEditor { editor ->
            if (editor is ShortcutEditorUiState.EditorState.Pinned) {
                updater(editor)
            } else {
                editor
            }
        }
    }

    private fun updateDynamic(
        updater: (ShortcutEditorUiState.EditorState.Dynamic) -> ShortcutEditorUiState.EditorState.Dynamic,
    ) {
        updateEditor { editor ->
            if (editor is ShortcutEditorUiState.EditorState.Dynamic) {
                updater(editor)
            } else {
                editor
            }
        }
    }

    private fun setDynamicEditor(index: Int, serverId: Int?) {
        updateEditor {
            buildDynamicState(index = index, serverId = serverId ?: 0)
        }
    }

    private fun buildPinnedDraft(serverId: Int?): ShortcutDraft {
        val draft = ShortcutDraft.empty("")
        return if (serverId != null) draft.copy(serverId = serverId) else draft
    }

    private fun openPinnedEditor(shortcutId: String) {
        updateEditor {
            ShortcutEditorUiState.EditorState.Pinned(
                draftSeed = ShortcutDraft.empty(shortcutId),
                isCreated = true,
            )
        }
        viewModelScope.launch {
            val serverId = resolveServerId(shortcutsRepository.getServers())
            val pinnedShortcuts = shortcutsRepository.loadPinnedShortcuts()
            updatePinned { state ->
                val pinned = pinnedShortcuts.firstOrNull { it.id == shortcutId }
                if (pinned != null) {
                    state.copy(draftSeed = pinned, isCreated = true)
                } else {
                    val draftSeed = if (serverId != null) {
                        state.draftSeed.copy(serverId = serverId)
                    } else {
                        state.draftSeed
                    }
                    state.copy(draftSeed = draftSeed, isCreated = false)
                }
            }
        }
    }

    private fun buildDynamicState(index: Int, serverId: Int): ShortcutEditorUiState.EditorState.Dynamic {
        val initial = ShortcutEditorUiState.EditorState.Dynamic.initial(index)
        return initial.copy(draftSeed = initial.draftSeed.copy(serverId = serverId))
    }

    private fun resolveServerId(result: ServersResult): Int? {
        return when (result) {
            is ServersResult.Success -> result.defaultServerId
            ServersResult.NoServers -> null
        }
    }
}

private const val DYNAMIC_DRAFT_SEED_PREFIX = "dynamic_draft"

private fun dynamicDraftSeedId(index: Int): String {
    return "${DYNAMIC_DRAFT_SEED_PREFIX}_${index + 1}"
}
