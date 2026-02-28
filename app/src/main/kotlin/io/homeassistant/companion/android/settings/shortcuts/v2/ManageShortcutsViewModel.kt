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
internal data class AppShortcutItem(val index: Int, val summary: ShortcutSummary)

@Immutable
internal data class ShortcutsListState(
    val isLoading: Boolean = true,
    val error: ShortcutError? = null,
    val homeError: ShortcutError? = null,
    val maxAppShortcuts: Int? = null,
    val appItems: List<AppShortcutItem> = emptyList(),
    val homeItems: List<ShortcutSummary> = emptyList(),
) {
    val hasError: Boolean get() = error != null
    val isHomeSupported: Boolean get() = homeError != ShortcutError.HomeShortcutNotSupported
    val isEmpty: Boolean get() = appItems.isEmpty() && homeItems.isEmpty()
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

            val appItems = listData.appShortcuts.orderedShortcuts
                .map { (index, draft) -> AppShortcutItem(index = index, summary = draft.toSummary()) }
                .toList()

            val homeItems = listData.homeShortcuts.toList()
            _uiState.update {
                ShortcutsListState(
                    isLoading = false,
                    error = null,
                    homeError = listData.homeShortcutsError,
                    maxAppShortcuts = listData.appShortcuts.maxAppShortcuts,
                    appItems = appItems,
                    homeItems = homeItems,
                )
            }
        }
    }
}

internal sealed interface ShortcutsListAction {
    data class EditAppShortcut(val index: Int) : ShortcutsListAction
    data class EditHomeShortcut(val id: String) : ShortcutsListAction
    data object CreateAppShortcut : ShortcutsListAction
    data object CreateHomeShortcut : ShortcutsListAction
}
