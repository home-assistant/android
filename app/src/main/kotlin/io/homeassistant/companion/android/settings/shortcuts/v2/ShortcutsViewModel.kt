package io.homeassistant.companion.android.settings.shortcuts.v2

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.shortcuts.ShortcutsRepository
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.DynamicShortcutsData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutResult
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutSummary
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutsListData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.toSummary
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Immutable
internal data class DynamicShortcutItem(val index: Int, val summary: ShortcutSummary)

@Immutable
internal data class ShortcutsListState(
    val isLoading: Boolean = true,
    val dynamicItems: ImmutableList<DynamicShortcutItem> = persistentListOf(),
    val pinnedShortcuts: ImmutableList<ShortcutSummary> = persistentListOf(),
) {
    val isEmpty: Boolean get() = dynamicItems.isEmpty() && pinnedShortcuts.isEmpty()
}

@RequiresApi(Build.VERSION_CODES.N_MR1)
@HiltViewModel
internal class ShortcutsViewModel @Inject constructor(private val shortcutsRepository: ShortcutsRepository) :
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
                _uiState.value = previous.copy(isLoading = true)
            }

            val listData = when (val result = shortcutsRepository.loadShortcutsList()) {
                is ShortcutResult.Success -> result.data
                is ShortcutResult.Error -> ShortcutsListData(
                    dynamic = DynamicShortcutsData(
                        maxDynamicShortcuts = 0,
                        shortcuts = emptyMap(),
                    ),
                    pinned = emptyList(),
                    pinnedError = result.error,
                )
            }

            val dynamicItems = listData.dynamic.shortcuts
                .entries
                .sortedBy { it.key }
                .map { (index, draft) -> DynamicShortcutItem(index, draft.toSummary()) }
                .toImmutableList()

            _uiState.value = ShortcutsListState(
                isLoading = false,
                dynamicItems = dynamicItems,
                pinnedShortcuts = listData.pinned.toImmutableList(),
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
