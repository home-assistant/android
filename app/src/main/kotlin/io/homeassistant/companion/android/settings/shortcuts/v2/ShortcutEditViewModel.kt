package io.homeassistant.companion.android.settings.shortcuts.v2

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.shortcuts.ShortcutsRepository
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.PinResult
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutDraft
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.empty
import io.homeassistant.companion.android.settings.shortcuts.v2.ui.screens.ShortcutEditAction
import io.homeassistant.companion.android.settings.shortcuts.v2.ui.screens.ShortcutEditorScreenState
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
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
        data class Pinned(
            override val draftSeed: ShortcutDraft,
            val pinnedIds: ImmutableList<String>,
        ) : EditorState {
            companion object {
                fun initial() = Pinned(
                    draftSeed = ShortcutDraft.empty(""),
                    pinnedIds = persistentListOf(),
                )
            }
        }

        @Immutable
        data class Dynamic(
            val selectedIndex: Int,
            override val draftSeed: ShortcutDraft,
            val isCreated: Boolean,
        ) : EditorState {
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
    private val _deleteEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private var currentServerId: Int = 0

    val uiState: StateFlow<ShortcutEditorUiState> = _uiState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = _uiState.value,
    )
    val pinResultEvents = _pinResultEvents.asSharedFlow()
    val deleteEvents = _deleteEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            loadData()
        }
    }

    private suspend fun loadData() {
        try {
            currentServerId = shortcutsRepository.currentServerId()

            val servers = shortcutsRepository.getServers().toImmutableList()
            updateScreen { it.copy(servers = servers) }
            applyServerIdToSeed(resolveAvailableServerId())

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
            openPinnedEditor { state, _ -> state
        }
    }

    fun editPinned(shortcutId: String) {
        if (!canPinShortcuts) return
        openPinnedEditor { state, pinnedShortcuts ->
            val pinned = pinnedShortcuts.firstOrNull { it.id == shortcutId }
            state.copy(draftSeed = pinned ?: state.draftSeed)
        }
    }

    fun openDynamic(index: Int) {
        if (index !in 0 until maxDynamicShortcuts) return
        setDynamicEditor(index)
        viewModelScope.launch {
            refreshDynamicShortcuts(updateSeed = true)
        }
    }

    fun createDynamicFirstAvailable() {
        viewModelScope.launch {
            val dynamicDrafts = shortcutsRepository.loadDynamicShortcuts()
            val index = (0 until maxDynamicShortcuts).firstOrNull { candidate ->
                !dynamicDrafts.containsKey(candidate)
            } ?: return@launch
            setDynamicEditor(index)
            refreshDynamicShortcuts(updateSeed = true)
        }
    }

    private fun upsertPinned(draft: ShortcutDraft) {
        if (!canPinShortcuts) return
        viewModelScope.launch {
            val result = shortcutsRepository.upsertPinnedShortcut(draft)
            handlePinResult(result)
            refreshPinnedShortcuts()
        }
    }

    private fun deletePinned(shortcutId: String) {
        if (!canPinShortcuts) return
        if (shortcutId.isBlank()) return
        viewModelScope.launch {
            shortcutsRepository.deletePinnedShortcut(shortcutId)
            _deleteEvents.emit(Unit)
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

    private fun applyServerIdToSeed(serverId: Int) {
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

    private suspend fun refreshPinnedShortcuts() {
        if (!canPinShortcuts) return
        if (_uiState.value.editor !is ShortcutEditorUiState.EditorState.Pinned) return
        val pinnedShortcuts = shortcutsRepository.loadPinnedShortcuts()
        updatePinned { state ->
            state.copy(pinnedIds = pinnedShortcuts.map { it.id }.toImmutableList())
        }
        Timber.d("We have ${pinnedShortcuts.size} pinned shortcuts")
    }

    private suspend fun handlePinResult(result: PinResult) {
        _pinResultEvents.emit(result)
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
            _deleteEvents.emit(Unit)
        }
    }

    private fun updateEditor(
        updater: (ShortcutEditorUiState.EditorState) -> ShortcutEditorUiState.EditorState,
    ) {
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

    private fun setDynamicEditor(index: Int) {
        updateEditor {
            buildDynamicState(index = index, serverId = resolveAvailableServerId())
        }
    }

    private fun buildPinnedState(serverId: Int): ShortcutEditorUiState.EditorState.Pinned {
        val initial = ShortcutEditorUiState.EditorState.Pinned.initial()
        return initial.copy(draftSeed = initial.draftSeed.copy(serverId = serverId))
    }

    private fun openPinnedEditor(
        updater: (ShortcutEditorUiState.EditorState.Pinned, List<ShortcutDraft>) -> ShortcutEditorUiState.EditorState.Pinned,
    ) {
        updateEditor { buildPinnedState(resolveAvailableServerId()) }
        viewModelScope.launch {
            val pinnedShortcuts = shortcutsRepository.loadPinnedShortcuts()
            val pinnedIds = pinnedShortcuts.map { it.id }.toImmutableList()
            updatePinned { state ->
                updater(state, pinnedShortcuts).copy(pinnedIds = pinnedIds)
            }
        }
    }

    private fun buildDynamicState(
        index: Int,
        serverId: Int,
    ): ShortcutEditorUiState.EditorState.Dynamic {
        val initial = ShortcutEditorUiState.EditorState.Dynamic.initial(index)
        return initial.copy(draftSeed = initial.draftSeed.copy(serverId = serverId))
    }

    private fun resolveAvailableServerId(): Int {
        val servers = _uiState.value.screen.servers
        val editorServerId = _uiState.value.editor.draftSeed.serverId
        if (editorServerId != 0 && servers.any { it.id == editorServerId }) {
            return editorServerId
        }
        return servers.firstOrNull { it.id == currentServerId }?.id
            ?: servers.firstOrNull()?.id
            ?: 0
    }
}

private const val DYNAMIC_DRAFT_SEED_PREFIX = "dynamic_draft"

private fun dynamicDraftSeedId(index: Int): String {
    return "${DYNAMIC_DRAFT_SEED_PREFIX}_${index + 1}"
}
