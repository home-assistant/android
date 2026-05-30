package io.homeassistant.companion.android.tiles.dashboard

import android.content.Context
import android.os.Build
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.tiles.EventBuilders
import androidx.wear.tiles.RequestBuilders.ResourcesRequest
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.prefs.WearPrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.wear.dashboard.WearDashboardRepository
import io.homeassistant.companion.android.common.data.wear.dashboard.state.WearDashboardResolvedState
import io.homeassistant.companion.android.common.data.wear.dashboard.state.WearDashboardStateCache
import io.homeassistant.companion.android.common.data.wear.dashboard.state.getResolvedState
import io.homeassistant.companion.android.common.data.wear.dashboard.model.ScreenShape
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardCapabilities
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardRefreshPolicy
import io.homeassistant.companion.android.home.HomeActivity
import io.homeassistant.companion.android.tiles.MODIFIER_CLICK_REFRESH
import io.homeassistant.companion.android.tiles.RESOURCE_REFRESH
import io.homeassistant.companion.android.tiles.getNotConfiguredTimeline
import io.homeassistant.companion.android.tiles.getRefreshButton
import io.homeassistant.companion.android.tiles.getRefreshModifiers
import io.homeassistant.companion.android.tiles.hapticClick
import io.homeassistant.companion.android.tiles.loggedOutTimeline
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber

@AndroidEntryPoint
class WearDashboardTile : TileService() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var wearPrefsRepository: WearPrefsRepository

    @Inject
    lateinit var dashboardRepository: WearDashboardRepository

    @Inject
    lateinit var stateCache: WearDashboardStateCache

    @Inject
    lateinit var renderer: ProtoLayoutWearDashboardRenderer

    @Inject
    lateinit var actionSerializer: WearDashboardActionSerializer

    override fun onTileRequest(requestParams: TileRequest): ListenableFuture<Tile> = serviceScope.future {
        val tileId = requestParams.tileId
        val lastClickableId = requestParams.currentState.lastClickableId

        if (lastClickableId.isNotEmpty()) {
            handleTileClick(tileId, lastClickableId)
        }

        val dashboardId = dashboardRepository.getDashboardTileAssignmentAndSaveTileId(tileId)
        val dashboard = dashboardId?.let { dashboardRepository.getDashboard(it) }
        val freshnessMillis = dashboard?.let { freshnessIntervalMillis(it.refreshPolicy) } ?: 0L
        val resourcesVersion = "${dashboard?.id ?: "empty"}:${stateCache.getState(dashboardId.orEmpty())?.values?.hashCode() ?: 0}"

        Tile.Builder()
            .setResourcesVersion(resourcesVersion)
            .setFreshnessIntervalMillis(freshnessMillis)
            .setTileTimeline(
                if (!serverManager.isRegistered()) {
                    loggedOutTimeline(
                        this@WearDashboardTile,
                        requestParams,
                        commonR.string.wear_dashboard_tile,
                        commonR.string.wear_dashboard_tile_log_in,
                    )
                } else if (dashboard == null || dashboardId == null) {
                    getNotConfiguredTimeline(
                        this@WearDashboardTile,
                        requestParams,
                        commonR.string.wear_dashboard_tile,
                        HomeActivity.Companion.LaunchMode.DashboardTile,
                    )
                } else {
                    timeline(requestParams, dashboard, dashboardId)
                },
            )
            .build()
    }

    override fun onTileResourcesRequest(requestParams: ResourcesRequest): ListenableFuture<Resources> =
        serviceScope.future {
            val dashboardId = dashboardRepository.getDashboardTileAssignmentAndSaveTileId(requestParams.tileId)
            val dashboard = dashboardId?.let { dashboardRepository.getDashboard(it) }
            val pageId = dashboard?.surfaces?.tile?.page
            val resolvedState = dashboardId?.let { stateCache.getState(it) }
                ?: WearDashboardResolvedState()

            if (dashboard != null && pageId != null) {
                renderer.renderWithContext(
                    context = this@WearDashboardTile,
                    deviceParams = requestParams.deviceConfiguration,
                    config = dashboard,
                    pageId = pageId,
                    state = resolvedState,
                    capabilities = capabilitiesFor(requestParams.deviceConfiguration),
                )
            }

            Resources.Builder()
                .setVersion(requestParams.version)
                .apply {
                    WearDashboardTileResources.addIconResources(
                        context = this@WearDashboardTile,
                        builder = this,
                        iconResourceIds = renderer.iconResourceIds,
                        screenDensity = requestParams.deviceConfiguration.screenDensity,
                    )
                    addIdToImageMapping(
                        RESOURCE_REFRESH,
                        ResourceBuilders.ImageResource.Builder()
                            .setAndroidResourceByResId(
                                ResourceBuilders.AndroidImageResourceByResId.Builder()
                                    .setResourceId(io.homeassistant.companion.android.R.drawable.ic_refresh)
                                    .build(),
                            )
                            .build(),
                    )
                }
                .build()
        }

    override fun onTileAddEvent(requestParams: EventBuilders.TileAddEvent): Unit = runBlocking {
        withContext(Dispatchers.IO) {
            dashboardRepository.getDashboardTileAssignmentAndSaveTileId(requestParams.tileId)
        }
    }

    override fun onTileRemoveEvent(requestParams: EventBuilders.TileRemoveEvent): Unit = runBlocking {
        withContext(Dispatchers.IO) {
            dashboardRepository.removeDashboardTileAssignment(requestParams.tileId)
        }
    }

    override fun onTileEnterEvent(requestParams: EventBuilders.TileEnterEvent) {
        serviceScope.launch {
            try {
                getUpdater(this@WearDashboardTile).requestUpdate(WearDashboardTile::class.java)
            } catch (e: Exception) {
                Timber.w(e, "Unable to request dashboard tile update on enter")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    private suspend fun handleTileClick(tileId: Int, clickableId: String) {
        if (clickableId == MODIFIER_CLICK_REFRESH) {
            if (wearPrefsRepository.getWearHapticFeedback()) {
                hapticClick(applicationContext)
            }
            return
        }

        actionSerializer.getAction(clickableId)?.let { action ->
            WearDashboardActionReceiver.sendAction(
                context = applicationContext,
                actionPayload = actionSerializer.serializeAction(action),
                tileId = tileId,
            )
        }
    }

    private fun timeline(
        requestParams: TileRequest,
        dashboard: io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardConfig,
        dashboardId: String,
    ): Timeline {
        val pageId = dashboard.surfaces.tile?.page
        if (pageId == null) {
            return Timeline.fromLayoutElement(
                androidx.wear.protolayout.LayoutElementBuilders.Text.Builder()
                    .setText(getString(commonR.string.wear_dashboard_tile_no_surface))
                    .build(),
            )
        }

        val resolvedState = stateCache.getState(dashboardId) ?: WearDashboardResolvedState()
        val layout = renderer.renderWithContext(
            context = this,
            deviceParams = requestParams.deviceConfiguration,
            config = dashboard,
            pageId = pageId,
            state = resolvedState,
            capabilities = capabilitiesFor(requestParams.deviceConfiguration),
        )

        val root = androidx.wear.protolayout.LayoutElementBuilders.Box.Builder()
            .addContent(layout)
            .addContent(getRefreshButton())
            .setModifiers(getRefreshModifiers())
            .build()

        return Timeline.fromLayoutElement(root)
    }

    private fun capabilitiesFor(
        deviceConfig: androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters,
    ): WearDashboardCapabilities {
        val shape = if (deviceConfig.screenWidthDp == deviceConfig.screenHeightDp) {
            ScreenShape.Round
        } else {
            ScreenShape.Square
        }
        return WearDashboardCapabilities.fromDeviceParameters(
            screenWidthDp = deviceConfig.screenWidthDp,
            screenHeightDp = deviceConfig.screenHeightDp,
            screenShape = shape,
            sdkInt = Build.VERSION.SDK_INT,
        )
    }

    private fun freshnessIntervalMillis(policy: WearDashboardRefreshPolicy): Long = when (policy) {
        is WearDashboardRefreshPolicy.Interval -> policy.seconds.coerceAtLeast(1) * 1_000L
        WearDashboardRefreshPolicy.Manual -> 0L
        WearDashboardRefreshPolicy.OnEnter,
        WearDashboardRefreshPolicy.OnEntityChange,
        -> 60_000L
    }

    companion object {
        fun requestUpdate(context: Context) {
            getUpdater(context).requestUpdate(WearDashboardTile::class.java)
        }
    }
}
