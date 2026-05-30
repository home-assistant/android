package io.homeassistant.companion.android.home.views.dashboard

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.wear.dashboard.WearDashboardRepository
import io.homeassistant.companion.android.common.data.wear.dashboard.model.ScreenShape
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardAction
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardCapabilities
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardConfig
import io.homeassistant.companion.android.common.data.wear.dashboard.state.WearDashboardResolvedState
import io.homeassistant.companion.android.common.data.wear.dashboard.state.WearDashboardStateCache
import io.homeassistant.companion.android.common.data.wear.dashboard.state.WearDashboardUpdateCoordinator
import io.homeassistant.companion.android.dashboard.ongoing.WearDashboardOngoingActivityManager
import io.homeassistant.companion.android.tiles.dashboard.WearDashboardActionExecutor
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val ARG_DASHBOARD_ID = "dashboardId"
private const val ARG_PAGE_ID = "pageId"

/** UI state for the full-screen Wear dashboard. */
@Immutable
data class WearDashboardUiState(
    val isLoading: Boolean = true,
    val config: WearDashboardConfig? = null,
    val pageId: String? = null,
    val resolvedState: WearDashboardResolvedState = WearDashboardResolvedState(),
    val composeContent: WearDashboardComposeContent? = null,
    val capabilities: WearDashboardCapabilities = WearDashboardCapabilities.defaults(),
)

/**
 * Loads dashboard config and resolved state for the full-screen Wear dashboard screen.
 */
@HiltViewModel
class WearDashboardViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val dashboardRepository: WearDashboardRepository,
    private val stateCache: WearDashboardStateCache,
    private val updateCoordinator: WearDashboardUpdateCoordinator,
    private val composeRenderer: ComposeWearDashboardRenderer,
    private val actionExecutor: WearDashboardActionExecutor,
    private val ongoingActivityManager: WearDashboardOngoingActivityManager,
    private val application: Application,
) : ViewModel() {

    private val dashboardId: String = checkNotNull(savedStateHandle[ARG_DASHBOARD_ID])

    private val _uiState = MutableStateFlow(WearDashboardUiState())
    val uiState: StateFlow<WearDashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val config = dashboardRepository.getDashboard(dashboardId)
            if (config == null) {
                _uiState.update { it.copy(isLoading = false, config = null) }
                return@launch
            }

            val pageId = savedStateHandle.get<String>(ARG_PAGE_ID)
                ?: config.surfaces.app?.startPage
                ?: config.pages.firstOrNull()?.id

            updateCoordinator.startTracking(dashboardId)
            stateCache.observeState(dashboardId).collect { resolved ->
                val state = resolved ?: WearDashboardResolvedState()
                val capabilities = WearDashboardCapabilities.fromDeviceParameters(
                    screenWidthDp = 200,
                    screenHeightDp = 200,
                    screenShape = ScreenShape.Round,
                    sdkInt = Build.VERSION.SDK_INT,
                )
                val content = pageId?.let { currentPage ->
                    composeRenderer.renderInteractive(
                        config = config,
                        pageId = currentPage,
                        state = state,
                        capabilities = capabilities,
                        onAction = { action -> onDashboardAction(application, action) },
                    )
                }
                ongoingActivityManager.sync(config, state)
                _uiState.update {
                    WearDashboardUiState(
                        isLoading = false,
                        config = config,
                        pageId = pageId,
                        resolvedState = state,
                        composeContent = content,
                        capabilities = capabilities,
                    )
                }
            }
        }
    }

    /** Handles a dashboard component action from the Compose UI. */
    fun onDashboardAction(context: Context, action: WearDashboardAction) {
        viewModelScope.launch {
            when (action) {
                is WearDashboardAction.Navigate -> {
                    val config = _uiState.value.config ?: return@launch
                    val capabilities = _uiState.value.capabilities
                    val state = _uiState.value.resolvedState
                    val content = composeRenderer.renderInteractive(
                        config = config,
                        pageId = action.pageId,
                        state = state,
                        capabilities = capabilities,
                        onAction = { nested -> onDashboardAction(context, nested) },
                    )
                    _uiState.update { it.copy(pageId = action.pageId, composeContent = content) }
                }
                else -> actionExecutor.execute(context, action, tileId = null)
            }
        }
    }

    override fun onCleared() {
        updateCoordinator.stopTracking(dashboardId)
        ongoingActivityManager.stop(dashboardId)
        super.onCleared()
    }
}
