package io.homeassistant.companion.android.settings.shortcuts.v2

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.shortcuts.ShortcutsRepository
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutSummary
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.toSummary
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
internal data class DynamicShortcutItem(val index: Int, val summary: ShortcutSummary)

internal sealed interface ShortcutsListUiState {
    val canPinShortcuts: Boolean
    val canCreateDynamic: Boolean

    @Immutable
    data class Loading(override val canPinShortcuts: Boolean, override val canCreateDynamic: Boolean) :
        ShortcutsListUiState

    @Immutable
    data class Empty(
        override val canPinShortcuts: Boolean,
        override val canCreateDynamic: Boolean,
        val hasServers: Boolean,
    ) : ShortcutsListUiState

    @Immutable
    data class Content(
        override val canPinShortcuts: Boolean,
        override val canCreateDynamic: Boolean,
        val dynamicItems: ImmutableList<DynamicShortcutItem>,
        val pinnedShortcuts: ImmutableList<ShortcutSummary>,
    ) : ShortcutsListUiState

    companion object {
        fun initial(canPinShortcuts: Boolean, maxDynamicShortcuts: Int): ShortcutsListUiState {
            return Loading(
                canPinShortcuts = canPinShortcuts,
                canCreateDynamic = maxDynamicShortcuts > 0,
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.N_MR1)
@HiltViewModel
internal class ShortcutsViewModel @Inject constructor(private val shortcutsRepository: ShortcutsRepository) :
    ViewModel() {

    private val canPinShortcuts = shortcutsRepository.canPinShortcuts
    private val maxDynamicShortcuts = shortcutsRepository.maxDynamicShortcuts
    private val _uiState = MutableStateFlow(ShortcutsListUiState.initial(canPinShortcuts, maxDynamicShortcuts))

    val uiState: StateFlow<ShortcutsListUiState> = _uiState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = _uiState.value,
    )

    init {
        refresh()
    }

    fun refresh(showLoading: Boolean = _uiState.value is ShortcutsListUiState.Loading) {
        viewModelScope.launch {
            val previousState = _uiState.value
            if (showLoading) {
                _uiState.value = ShortcutsListUiState.Loading(
                    canPinShortcuts = previousState.canPinShortcuts,
                    canCreateDynamic = previousState.canCreateDynamic,
                )
            }
            try {
                val servers = shortcutsRepository.getServers().toImmutableList()
                val dynamicDrafts = shortcutsRepository.loadDynamicShortcuts()
                val dynamicItems = (0 until maxDynamicShortcuts).mapNotNull { index ->
                    dynamicDrafts[index]
                        ?.toSummary(isCreated = true)
                        ?.let { summary -> DynamicShortcutItem(index, summary) }
                }.toImmutableList()
                val canCreateDynamic = (0 until maxDynamicShortcuts).any { index ->
                    !dynamicDrafts.containsKey(index)
                }
                val pinnedSummaries = if (canPinShortcuts) {
                    shortcutsRepository.loadPinnedShortcuts()
                        .map { it.toSummary(isCreated = true) }
                        .toImmutableList()
                } else {
                    persistentListOf()
                }
                val hasServers = servers.isNotEmpty()

                _uiState.value = if (dynamicItems.isEmpty() && pinnedSummaries.isEmpty()) {
                    ShortcutsListUiState.Empty(
                        canPinShortcuts = canPinShortcuts,
                        canCreateDynamic = canCreateDynamic,
                        hasServers = hasServers,
                    )
                } else {
                    ShortcutsListUiState.Content(
                        canPinShortcuts = canPinShortcuts,
                        canCreateDynamic = canCreateDynamic,
                        dynamicItems = dynamicItems,
                        pinnedShortcuts = pinnedSummaries,
                    )
                }
            } catch (e: Exception) {
                _uiState.value = when (previousState) {
                    is ShortcutsListUiState.Loading -> ShortcutsListUiState.Empty(
                        canPinShortcuts = previousState.canPinShortcuts,
                        canCreateDynamic = previousState.canCreateDynamic,
                        hasServers = false,
                    )

                    else -> previousState
                }
            }
        }
    }
}

internal sealed interface ShortcutsListAction {
    data class EditDynamic(val index: Int) : ShortcutsListAction
    data class EditPinned(val id: String) : ShortcutsListAction
    data object CreateDynamic : ShortcutsListAction
    data object CreatePinned : ShortcutsListAction
}
