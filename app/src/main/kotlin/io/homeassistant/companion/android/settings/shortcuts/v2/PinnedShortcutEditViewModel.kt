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
data class PinnedShortcutEditorUiState(
    val screen: ShortcutEditorScreenState,
    val draftSeed: ShortcutDraft,
    val pinnedIds: ImmutableList<String>,
) {
    companion object {
        fun initial(): PinnedShortcutEditorUiState {
            val screenState = ShortcutEditorScreenState(
                isLoading = true,
            )

            return PinnedShortcutEditorUiState(
                screen = screenState,
                draftSeed = ShortcutDraft.empty(""),
                pinnedIds = persistentListOf(),
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.N_MR1)
@HiltViewModel
class PinnedShortcutEditViewModel @Inject constructor(private val shortcutsRepository: ShortcutsRepository) :
    ViewModel() {

    private val canPinShortcuts = shortcutsRepository.canPinShortcuts

    private val _uiState = MutableStateFlow(PinnedShortcutEditorUiState.initial())
    private val _pinResultEvents = MutableSharedFlow<PinResult>(extraBufferCapacity = 1)
    private val _deleteEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val uiState: StateFlow<PinnedShortcutEditorUiState> = _uiState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = _uiState.value,
    )
    val pinResultEvents = _pinResultEvents.asSharedFlow()
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

                refreshPinnedShortcuts()
            } finally {
                updateScreen { it.copy(isLoading = false) }
            }
        }
    }

    fun dispatch(action: ShortcutEditAction) {
        when (action) {
            is ShortcutEditAction.Submit -> createCurrent(action.draft)
            is ShortcutEditAction.Delete -> deleteCurrent(action.draftId)
        }
    }

    fun newPinned() {
        if (!canPinShortcuts) return
        // TODO: Improve
        val fallbackServerId = _uiState.value.draftSeed.serverId.takeIf { it != 0 }
            ?: _uiState.value.screen.servers.firstOrNull()?.id
            ?: 0
        _uiState.update { state ->
            state.copy(
                draftSeed = ShortcutDraft.empty("").copy(serverId = fallbackServerId),
            )
        }
    }

    fun editPinned(shortcutId: String) {
        if (!canPinShortcuts) return
        viewModelScope.launch {
            val pinnedShortcuts = shortcutsRepository.loadPinnedShortcuts()
            updatePinnedIds(pinnedShortcuts.map { it.id })
            val pinned = pinnedShortcuts.firstOrNull { it.id == shortcutId } ?: return@launch
            _uiState.update { state ->
                state.copy(draftSeed = pinned)
            }
        }
    }

    private fun createCurrent(draft: ShortcutDraft) {
        if (!canPinShortcuts) return
        viewModelScope.launch {
            val result = shortcutsRepository.upsertPinnedShortcut(draft)
            handlePinResult(result)
            refreshPinnedShortcuts()
        }
    }

    private fun deleteCurrent(shortcutId: String) {
        if (!canPinShortcuts) return
        if (shortcutId.isBlank()) return
        viewModelScope.launch {
            shortcutsRepository.deletePinnedShortcut(shortcutId)
            _deleteEvents.emit(Unit)
        }
    }

    private fun applyServerIdToSeed(serverId: Int) {
        _uiState.update { state ->
            state.copy(
                draftSeed = state.draftSeed.copy(serverId = serverId),
            )
        }
    }

    private suspend fun refreshPinnedShortcuts() {
        if (!canPinShortcuts) return
        val pinnedShortcuts = shortcutsRepository.loadPinnedShortcuts()
        updatePinnedIds(pinnedShortcuts.map { it.id })
        Timber.d("We have ${pinnedShortcuts.size} pinned shortcuts")
    }

    private fun updatePinnedIds(ids: List<String>) {
        _uiState.update { state ->
            state.copy(pinnedIds = ids.toImmutableList())
        }
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
}
