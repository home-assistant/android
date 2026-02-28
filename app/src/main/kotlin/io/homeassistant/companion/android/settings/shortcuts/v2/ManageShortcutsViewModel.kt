package io.homeassistant.companion.android.settings.shortcuts.v2

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.shortcuts.ShortcutsRepository
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutError
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutResult
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutSummary
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.toSummary
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
internal data class DynamicShortcutItem(val index: Int, val summary: ShortcutSummary)

@Immutable
internal data class ShortcutsListState(
    val isLoading: Boolean = true,
    val error: ShortcutError? = null,
    val pinnedError: ShortcutError? = null,
    val maxDynamicShortcuts: Int? = null,
    val dynamicItems: List<DynamicShortcutItem> = emptyList(),
    val pinnedItems: List<ShortcutSummary> = emptyList(),
) {
    val hasError: Boolean get() = error != null
    val isPinSupported: Boolean get() = pinnedError != ShortcutError.PinnedNotSupported
    val isEmpty: Boolean get() = dynamicItems.isEmpty() && pinnedItems.isEmpty()
}

@HiltViewModel
internal class ManageShortcutsViewModel @Inject constructor(private val shortcutsRepository: ShortcutsRepository) :
    ViewModel() {
    private val _uiState = MutableStateFlow(ShortcutsListState())
    val uiState: StateFlow<ShortcutsListState> = _uiState.asStateFlow()
    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        refreshInternal(true)
    }

    fun refreshSilently() {
        refreshInternal(false)
    }

    private fun refreshInternal(showLoading: Boolean) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            if (showLoading) {
                _uiState.update {
                    it.copy(isLoading = true, error = null)
                }
            }

            val listData = when (val result = shortcutsRepository.loadShortcutsList()) {
                is ShortcutResult.Success -> result.data
                is ShortcutResult.Error -> {
                    _uiState.update {
                        ShortcutsListState(
                            isLoading = false,
                            error = result.error,
                        )
                    }
                    return@launch
                }
            }

            val dynamicItems = listData.dynamic.orderedShortcuts
                .map { (index, draft) -> DynamicShortcutItem(index, draft.toSummary()) }
                .toList()

            val pinnedItems = listData.pinned.toList()
            _uiState.update {
                ShortcutsListState(
                    isLoading = false,
                    error = null,
                    pinnedError = listData.pinnedError,
                    maxDynamicShortcuts = listData.dynamic.maxDynamicShortcuts,
                    dynamicItems = dynamicItems,
                    pinnedItems = pinnedItems,
                )
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
