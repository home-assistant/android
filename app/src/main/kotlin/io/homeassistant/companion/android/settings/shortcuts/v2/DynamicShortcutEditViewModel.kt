package io.homeassistant.companion.android.settings.shortcuts.v2

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.shortcuts.ShortcutsRepository
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutDraft
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.empty
import io.homeassistant.companion.android.settings.shortcuts.v2.ui.screens.ShortcutEditAction
import io.homeassistant.companion.android.settings.shortcuts.v2.ui.screens.ShortcutEditorScreenState
import javax.inject.Inject
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
data class DynamicShortcutEditorUiState(
    val screen: ShortcutEditorScreenState,
    val selectedIndex: Int,
    val draftSeed: ShortcutDraft,
    val isCreated: Boolean,
) {
    companion object {
        fun initial(draftId: String): DynamicShortcutEditorUiState {
            val draftSeed = ShortcutDraft.empty(draftId)

            val screenState = ShortcutEditorScreenState(
                isLoading = true,
            )

            return DynamicShortcutEditorUiState(
                screen = screenState,
                selectedIndex = 0,
                draftSeed = draftSeed,
                isCreated = false,
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.N_MR1)
@HiltViewModel
class DynamicShortcutEditViewModel @Inject constructor(private val shortcutsRepository: ShortcutsRepository) :
    ViewModel() {

    private val maxDynamicShortcuts = shortcutsRepository.maxDynamicShortcuts
    private val _uiState = MutableStateFlow(
        DynamicShortcutEditorUiState.initial(dynamicDraftSeedId(0)),
    )
    private val _deleteEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val uiState: StateFlow<DynamicShortcutEditorUiState> = _uiState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = _uiState.value,
    )
    val deleteEvents = _deleteEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            try {
                val currentServerId = shortcutsRepository.currentServerId()
                applyServerIdToSeed(currentServerId)

                val servers = shortcutsRepository.getServers().toImmutableList()
                updateScreen { it.copy(servers = servers) }

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

                refreshDynamicShortcuts(updateSeed = true)
            } finally {
                updateScreen { it.copy(isLoading = false) }
            }
        }
    }

    fun dispatch(action: ShortcutEditAction) {
        when (action) {
            is ShortcutEditAction.Submit -> createCurrent(action.draft)
            is ShortcutEditAction.Delete -> deleteCurrent()
        }
    }

    fun selectIndex(index: Int) {
        if (index !in 0 until maxDynamicShortcuts) return
        _uiState.update { state ->
            state.copy(
                selectedIndex = index,
                draftSeed = ShortcutDraft.empty(dynamicDraftSeedId(index)),
                isCreated = false,
            )
        }
        viewModelScope.launch {
            refreshDynamicShortcuts(updateSeed = true)
        }
    }

    fun selectFirstAvailable() {
        viewModelScope.launch {
            val dynamicDrafts = shortcutsRepository.loadDynamicShortcuts()
            val index = (0 until maxDynamicShortcuts).firstOrNull { candidate ->
                !dynamicDrafts.containsKey(candidate)
            } ?: return@launch
            _uiState.update { state ->
                state.copy(
                    selectedIndex = index,
                    draftSeed = ShortcutDraft.empty(dynamicDraftSeedId(index)),
                    isCreated = false,
                )
            }
        }
    }

    private fun createCurrent(draft: ShortcutDraft) {
        val index = _uiState.value.selectedIndex
        viewModelScope.launch {
            shortcutsRepository.upsertDynamicShortcut(index, draft)
            refreshDynamicShortcuts(updateSeed = false)
        }
    }

    private fun deleteCurrent() {
        val index = _uiState.value.selectedIndex
        deleteDynamicShortcut(index)
    }

    private fun applyServerIdToSeed(serverId: Int) {
        _uiState.update { state ->
            state.copy(
                draftSeed = state.draftSeed.copy(serverId = serverId),
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

    private suspend fun refreshDynamicShortcuts(updateSeed: Boolean) {
        val loadedDraftsByIndex = shortcutsRepository.loadDynamicShortcuts()
        updateEditor { state ->
            val selectedIndex = state.selectedIndex
            val loadedDraft = loadedDraftsByIndex[selectedIndex]
            state.copy(
                draftSeed = if (updateSeed) {
                    loadedDraft ?: ShortcutDraft.empty(dynamicDraftSeedId(selectedIndex))
                } else {
                    state.draftSeed
                },
                isCreated = loadedDraft != null,
            )
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

    private fun updateEditor(updater: (DynamicShortcutEditorUiState) -> DynamicShortcutEditorUiState) {
        _uiState.update(updater)
    }

    private fun dynamicDraftSeedId(index: Int): String {
        return "${DYNAMIC_DRAFT_SEED_PREFIX}_${index + 1}"
    }

    companion object {
        private const val DYNAMIC_DRAFT_SEED_PREFIX = "dynamic_draft"
    }
}
