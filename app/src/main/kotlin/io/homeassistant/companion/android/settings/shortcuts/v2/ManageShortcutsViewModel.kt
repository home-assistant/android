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
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
internal data class DynamicShortcutItem(val index: Int, val summary: ShortcutSummary)

@Immutable
internal data class ShortcutsListState(
    val isLoading: Boolean = true,
    val error: ShortcutError? = null,
    val isPinSupported: Boolean = false, // Not used for now
    val dynamicItems: ImmutableList<DynamicShortcutItem> = persistentListOf(),
    val pinnedItems: ImmutableList<ShortcutSummary> = persistentListOf(),
) {
    val hasError: Boolean get() = error != null
    val isEmpty: Boolean get() = dynamicItems.isEmpty() && pinnedItems.isEmpty()
}

@HiltViewModel
internal class ManageShortcutsViewModel @Inject constructor(private val shortcutsRepository: ShortcutsRepository) :
    ViewModel() {
    private val _uiState = MutableStateFlow(ShortcutsListState())
    val uiState: StateFlow<ShortcutsListState> = _uiState

    init {
        refresh(showLoading = true)
    }

    fun refresh(showLoading: Boolean = !_uiState.value.isLoading) {
        viewModelScope.launch {
            val previous = _uiState.value
            if (showLoading) {
                _uiState.value = previous.copy(isLoading = true, error = null)
            }

            val listData = when (val result = shortcutsRepository.loadShortcutsList()) {
                is ShortcutResult.Success -> result.data
                is ShortcutResult.Error -> {
                    _uiState.update {
                        it.copy(isLoading = false, error = result.error)
                    }
                    return@launch
                }
            }

            val dynamicItems = listData.dynamic.shortcuts
                .entries
                .sortedBy { it.key }
                .map { (index, draft) -> DynamicShortcutItem(index, draft.toSummary()) }
                .toImmutableList()

            val pinnedItems = listData.pinned.toImmutableList()
            val pinNotSupported = listData.pinnedError == ShortcutError.PinnedNotSupported
            _uiState.value = ShortcutsListState(
                isLoading = false,
                error = null,
                isPinSupported = !pinNotSupported,
                dynamicItems = dynamicItems,
                pinnedItems = pinnedItems,
            )
        }
    }
}

internal sealed interface ShortcutsListAction {
    data class EditDynamic(val index: Int) : ShortcutsListAction
    data class EditPinned(val id: String) : ShortcutsListAction
    data object CreateDynamic : ShortcutsListAction
    data object CreatePinned : ShortcutsListAction
}
