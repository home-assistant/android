package io.homeassistant.companion.android.tiles

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.tiles.EventBuilders
import androidx.wear.tiles.RequestBuilders.ResourcesRequest
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import com.mikepenz.iconics.IconicsColor
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial.Icon3
import com.mikepenz.iconics.utils.backgroundColor
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.getIcon
import io.homeassistant.companion.android.common.data.prefs.WearPrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.data.SimplifiedEntity
import io.homeassistant.companion.android.util.getIcon
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber

// Dimensions (dp)
private const val CIRCLE_SIZE = 56f
private const val ICON_SIZE_FULL = 48f * 0.7071f // square that fits in 48dp circle
private const val ICON_SIZE_SMALL = 40f * 0.7071f // square that fits in 48dp circle
private const val SPACING = 8f
private const val TEXT_SIZE = 8f
private const val TEXT_PADDING = 2f
private const val LOADING_RESOURCE_SUFFIX = "_loading"
private const val LOADING_VERSION_PREFIX = "|loading:"
private const val LOADING_SAFETY_TIMEOUT_MS = 45_000L

@AndroidEntryPoint
class ShortcutsTile : TileService() {
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var wearPrefsRepository: WearPrefsRepository

    override fun onTileRequest(requestParams: TileRequest): ListenableFuture<Tile> = serviceScope.future {
        val state = requestParams.currentState
        android.util.Log.e(
            TAG,
            "onTileRequest click=${state.lastClickableId} loading=$loadingEntityIds cache=${entityStates.size}",
        )
        if (state.lastClickableId.isNotEmpty()) {
            Intent().also { intent ->
                intent.action = "io.homeassistant.companion.android.TILE_ACTION"
                intent.putExtra("entity_id", state.lastClickableId)
                intent.setPackage(packageName)
                sendBroadcast(intent)
            }
            loadingEntityIds[state.lastClickableId] = System.currentTimeMillis()
        }

        // Clear stale loading entries (safety net for missed WebSocket events)
        val now = System.currentTimeMillis()
        loadingEntityIds.entries.removeAll { now - it.value > LOADING_SAFETY_TIMEOUT_MS }

        val tileId = requestParams.tileId
        val entities = getEntities(tileId)

        // Encode loading state in version string so onTileResourcesRequest can read it
        val loadingPart = if (loadingEntityIds.isNotEmpty()) {
            LOADING_VERSION_PREFIX + loadingEntityIds.keys.sorted().joinToString(",")
        } else {
            ""
        }
        val resourcesVersion = "$TAG$tileId.${now}$loadingPart"

        Tile.Builder()
            .setResourcesVersion(resourcesVersion)
            .setTileTimeline(
                if (serverManager.isRegistered()) {
                    timeline(tileId)
                } else {
                    loggedOutTimeline(
                        this@ShortcutsTile,
                        requestParams,
                        commonR.string.shortcuts,
                        commonR.string.shortcuts_tile_log_in,
                    )
                },
            ).build()
    }

    override fun onTileResourcesRequest(requestParams: ResourcesRequest): ListenableFuture<Resources> =
        serviceScope.future {
            // Parse loading entity IDs from version string (survives service recreation)
            val loadingIds = requestParams.version
                .substringAfter(LOADING_VERSION_PREFIX, "")
                .split(",")
                .filter { it.isNotEmpty() }
                .toSet()
            android.util.Log.e(
                TAG,
                "onTileResourcesRequest loading=$loadingIds states=${entityStates.mapValues { it.value.state }}",
            )

            val showLabels = wearPrefsRepository.getShowShortcutText()
            val iconSize = if (showLabels) ICON_SIZE_SMALL else ICON_SIZE_FULL
            val density = requestParams.deviceConfiguration.screenDensity
            val iconSizePx = (iconSize * density).roundToInt()
            val entities = getEntities(requestParams.tileId)

            Resources.Builder()
                .setVersion(requestParams.version)
                .apply {
                    entities.forEach { entity ->
                        // Use cached entity for state-aware icon, fall back to domain icon
                        val cachedEntity = entityStates[entity.entityId]
                        val iconIIcon = if (cachedEntity != null) {
                            cachedEntity.getIcon(this@ShortcutsTile)
                        } else {
                            getIcon(entity.icon, entity.domain, this@ShortcutsTile)
                        }
                        addIdToImageMapping(
                            entity.entityId,
                            buildIconResource(iconIIcon, iconSize, iconSizePx),
                        )
                    }

                    // Generate loading icons for tapped entities
                    loadingIds.forEach { loadingId ->
                        addIdToImageMapping(
                            loadingId + LOADING_RESOURCE_SUFFIX,
                            buildIconResource(Icon3.cmd_progress_clock, iconSize, iconSizePx),
                        )
                    }
                }
                .build()
        }

    override fun onTileEnterEvent(requestParams: EventBuilders.TileEnterEvent) {
        serviceScope.launch {
            // Clear loading — we're about to fetch fresh state
            loadingEntityIds.clear()

            val tileId = requestParams.tileId
            val entities = getEntities(tileId)
            val entityIds = entities.map { it.entityId }

            // Initial state fetch via batch WebSocket call
            if (serverManager.isRegistered()) {
                try {
                    val allEntities = serverManager.integrationRepository().getEntities()
                    allEntities?.filter { it.entityId in entityIds }?.forEach { entity ->
                        entityStates[entity.entityId] = entity
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Failed to fetch initial entity states for tile")
                }
            }

            // Render with fresh cache
            try {
                requestUpdate(this@ShortcutsTile)
            } catch (e: Exception) {
                Timber.w(e, "Unable to request tile update on enter")
            }

            // Start WebSocket subscription for real-time updates
            startEntitySubscription(
                serverManager = serverManager,
                entityIds = entityIds,
                context = this@ShortcutsTile,
            )
        }
    }

    override fun onTileLeaveEvent(requestParams: EventBuilders.TileLeaveEvent) {
        stopEntitySubscription()
    }

    override fun onTileAddEvent(requestParams: EventBuilders.TileAddEvent): Unit = runBlocking {
        withContext(Dispatchers.IO) {
            wearPrefsRepository.getTileShortcutsAndSaveTileId(requestParams.tileId)
        }
    }

    override fun onTileRemoveEvent(requestParams: EventBuilders.TileRemoveEvent): Unit = runBlocking {
        withContext(Dispatchers.IO) {
            wearPrefsRepository.removeTileShortcuts(requestParams.tileId)
        }
        stopEntitySubscription()
        entityStates.clear()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    private suspend fun getEntities(tileId: Int): List<SimplifiedEntity> {
        return wearPrefsRepository.getTileShortcutsAndSaveTileId(tileId).map { SimplifiedEntity(it) }
    }

    private suspend fun timeline(tileId: Int): Timeline {
        val entities = getEntities(tileId)
        val showLabels = wearPrefsRepository.getShowShortcutText()
        return Timeline.fromLayoutElement(layout(entities, showLabels))
    }

    private fun buildIconResource(
        icon: com.mikepenz.iconics.typeface.IIcon,
        iconSizeDp: Float,
        iconSizePx: Int,
    ): ResourceBuilders.ImageResource {
        val bitmap = IconicsDrawable(this, icon).apply {
            colorInt = Color.WHITE
            sizeDp = iconSizeDp.roundToInt()
            backgroundColor = IconicsColor.colorRes(R.color.colorOverlay)
        }.toBitmap(iconSizePx, iconSizePx, Bitmap.Config.RGB_565)
        val data = ByteBuffer.allocate(bitmap.byteCount).apply {
            bitmap.copyPixelsToBuffer(this)
        }.array()
        return ResourceBuilders.ImageResource.Builder()
            .setInlineResource(
                ResourceBuilders.InlineImageResource.Builder()
                    .setData(data)
                    .setWidthPx(iconSizePx)
                    .setHeightPx(iconSizePx)
                    .setFormat(ResourceBuilders.IMAGE_FORMAT_RGB_565)
                    .build(),
            ).build()
    }

    fun layout(entities: List<SimplifiedEntity>, showLabels: Boolean): LayoutElement = Column.Builder().apply {
        if (entities.isEmpty()) {
            addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(getString(commonR.string.shortcuts_tile_empty))
                    .build(),
            )
        } else {
            addContent(rowLayout(entities.subList(0, min(2, entities.size)), showLabels))
            if (entities.size > 2) {
                addContent(Spacer.Builder().setHeight(dp(SPACING)).build())
                addContent(rowLayout(entities.subList(2, min(5, entities.size)), showLabels))
            }
            if (entities.size > 5) {
                addContent(Spacer.Builder().setHeight(dp(SPACING)).build())
                addContent(rowLayout(entities.subList(5, min(7, entities.size)), showLabels))
            }
        }
    }
        .build()

    private fun rowLayout(entities: List<SimplifiedEntity>, showLabels: Boolean): LayoutElement = Row.Builder().apply {
        addContent(iconLayout(entities[0], showLabels))
        entities.drop(1).forEach { entity ->
            addContent(Spacer.Builder().setWidth(dp(SPACING)).build())
            addContent(iconLayout(entity, showLabels))
        }
    }
        .build()

    private fun iconLayout(entity: SimplifiedEntity, showLabels: Boolean): LayoutElement = Box.Builder().apply {
        val iconSize = if (showLabels) ICON_SIZE_SMALL else ICON_SIZE_FULL
        val resourceId = if (loadingEntityIds.containsKey(entity.entityId)) {
            entity.entityId + LOADING_RESOURCE_SUFFIX
        } else {
            entity.entityId
        }
        setWidth(dp(CIRCLE_SIZE))
        setHeight(dp(CIRCLE_SIZE))
        setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
        setModifiers(
            ModifiersBuilders.Modifiers.Builder()
                .setBackground(
                    ModifiersBuilders.Background.Builder()
                        .setColor(argb(ContextCompat.getColor(baseContext, R.color.colorOverlay)))
                        .setCorner(
                            ModifiersBuilders.Corner.Builder()
                                .setRadius(dp(CIRCLE_SIZE / 2))
                                .build(),
                        )
                        .build(),
                )
                .setClickable(
                    ModifiersBuilders.Clickable.Builder()
                        .setId(entity.entityId)
                        .setOnClick(
                            ActionBuilders.LoadAction.Builder().build(),
                        )
                        .build(),
                )
                .build(),
        )
        addContent(
            LayoutElementBuilders.Image.Builder()
                .setResourceId(resourceId)
                .setWidth(dp(iconSize))
                .setHeight(dp(iconSize))
                .build(),
        )
        if (showLabels) {
            addContent(
                LayoutElementBuilders.Arc.Builder()
                    .addContent(
                        LayoutElementBuilders.ArcText.Builder()
                            .setText(entity.friendlyName)
                            .setFontStyle(
                                LayoutElementBuilders.FontStyle.Builder()
                                    .setSize(sp(TEXT_SIZE))
                                    .build(),
                            )
                            .build(),
                    )
                    .setModifiers(
                        ModifiersBuilders.Modifiers.Builder()
                            .setPadding(
                                ModifiersBuilders.Padding.Builder()
                                    .setAll(dp(TEXT_PADDING))
                                    .build(),
                            ).build(),
                    )
                    .build(),
            )
        }
    }
        .build()

    companion object {
        private const val TAG = "ShortcutsTile"

        /** Entity cache — populated by WebSocket, read by tile rendering (zero network). */
        private val entityStates = ConcurrentHashMap<String, Entity>()

        /** Entities currently in loading state. Maps entityId → timestamp when loading started. */
        private val loadingEntityIds = ConcurrentHashMap<String, Long>()

        /** WebSocket subscription for entity state changes. Outlives service instances. */
        private var subscriptionJob: Job? = null
        private val subscriptionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        fun requestUpdate(context: Context) {
            getUpdater(context).requestUpdate(ShortcutsTile::class.java)
        }

        private fun startEntitySubscription(serverManager: ServerManager, entityIds: List<String>, context: Context) {
            if (subscriptionJob?.isActive == true) return
            val appContext = context.applicationContext
            subscriptionJob = subscriptionScope.launch {
                try {
                    val flow = serverManager.integrationRepository()
                        .getEntityUpdates(entityIds)
                    flow?.collect { entity ->
                        entityStates[entity.entityId] = entity
                        loadingEntityIds.remove(entity.entityId)
                        requestUpdate(appContext)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Entity subscription failed")
                }
            }
        }

        private fun stopEntitySubscription() {
            subscriptionJob?.cancel()
            subscriptionJob = null
        }
    }
}
