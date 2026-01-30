package io.homeassistant.companion.android.settings.shortcuts.v2

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.shortcuts.ShortcutsRepository
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.DynamicShortcutsData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutRepositoryError
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutRepositoryResult
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutSummary
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutsListData
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
        fun initial(): ShortcutsListUiState {
            return Loading(
                canPinShortcuts = true,
                canCreateDynamic = true,
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.N_MR1)
@HiltViewModel
internal class ShortcutsViewModel @Inject constructor(private val shortcutsRepository: ShortcutsRepository) :
    ViewModel() {

    private val _uiState = MutableStateFlow(ShortcutsListUiState.initial())

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
                val serversResult = shortcutsRepository.getServers()
                val servers = when (serversResult) {
                    is ShortcutRepositoryResult.Success -> serversResult.data.servers.toImmutableList()
                    is ShortcutRepositoryResult.Error -> persistentListOf()
                }
                val listResult = shortcutsRepository.loadShortcutsList()
                val listData = when (listResult) {
                    is ShortcutRepositoryResult.Success -> listResult.data
                    is ShortcutRepositoryResult.Error -> ShortcutsListData(
                        dynamic = DynamicShortcutsData(maxDynamicShortcuts = 0, shortcuts = emptyMap()),
                        pinned = emptyList(),
                        pinnedError = listResult.error,
                    )
                }
                val maxDynamicShortcuts = listData.dynamic.maxDynamicShortcuts
                val dynamicItems = (0 until maxDynamicShortcuts).mapNotNull { index ->
                    listData.dynamic.shortcuts[index]
                        ?.toSummary(isCreated = true)
                        ?.let { summary -> DynamicShortcutItem(index, summary) }
                }.toImmutableList()
                val canCreateDynamic = (0 until maxDynamicShortcuts).any { index ->
                    !listData.dynamic.shortcuts.containsKey(index)
                }
                val canPinShortcuts = listData.pinnedError != ShortcutRepositoryError.PinnedNotSupported
                val pinnedSummaries = listData.pinned
                    .map { it.toSummary(isCreated = true) }
                    .toImmutableList()
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
