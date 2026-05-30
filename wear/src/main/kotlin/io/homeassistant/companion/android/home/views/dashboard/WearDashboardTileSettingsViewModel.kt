package io.homeassistant.companion.android.home.views.dashboard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.wear.dashboard.WearDashboardRepository
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardConfig
import io.homeassistant.companion.android.common.data.wear.dashboard.state.WearDashboardStateCache
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val ARG_WEAR_DASHBOARD_TILE_ID = "wearDashboardTileId"

/** UI state for dashboard tile settings preview. */
data class WearDashboardTileSettingsUiState(
    val dashboard: WearDashboardConfig? = null,
    val previewText: String = "",
)

/**
 * Loads the dashboard assigned to a tile instance for read-only preview.
 */
@HiltViewModel
class WearDashboardTileSettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val dashboardRepository: WearDashboardRepository,
    private val stateCache: WearDashboardStateCache,
) : ViewModel() {

    private val tileId: Int = checkNotNull(savedStateHandle.get<Int>(ARG_WEAR_DASHBOARD_TILE_ID))

    private val _uiState = MutableStateFlow(WearDashboardTileSettingsUiState())
    val uiState: StateFlow<WearDashboardTileSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val dashboardId = dashboardRepository.getDashboardTileAssignmentAndSaveTileId(tileId)
            val dashboard = dashboardId?.let { dashboardRepository.getDashboard(it) }
            val preview = dashboardId?.let { id ->
                stateCache.getState(id)?.bindingValues?.values?.firstOrNull().orEmpty()
            }.orEmpty()
            _uiState.update { WearDashboardTileSettingsUiState(dashboard = dashboard, previewText = preview) }
        }
    }
}
