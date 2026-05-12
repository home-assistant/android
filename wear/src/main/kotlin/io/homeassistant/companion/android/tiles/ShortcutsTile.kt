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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

// Dimensions (dp)
private const val CIRCLE_SIZE = 56f
private const val ICON_SIZE_FULL = 48f * 0.7071f // square that fits in 48dp circle
private const val ICON_SIZE_SMALL = 40f * 0.7071f // square that fits in 48dp circle
private const val SPACING = 8f
private const val TEXT_SIZE = 8f
private const val TEXT_PADDING = 2f

// Bounded so a cold-cache fetch cannot exceed the Wear tile render timeout (~3s).
private const val CACHE_WARM_TIMEOUT_MS = 2000L

@AndroidEntryPoint
class ShortcutsTile : TileService() {
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var wearPrefsRepository: WearPrefsRepository

    override fun onTileRequest(requestParams: TileRequest): ListenableFuture<Tile> = serviceScope.future {
        val clicked = requestParams.currentState.lastClickableId
        if (clicked.isNotEmpty()) {
            Intent().also { intent ->
                intent.action = "io.homeassistant.companion.android.TILE_ACTION"
                intent.putExtra("entity_id", clicked)
                intent.setPackage(packageName)
                sendBroadcast(intent)
            }
        }

        val tileId = requestParams.tileId
        val entities = getEntities(tileId)
        val entityIds = entities.map { it.entityId }
        val isRegistered = serverManager.isRegistered()

        if (isRegistered) {
            val missing = entityIds.filterNot { entityStates.containsKey(it) }
            if (missing.isNotEmpty()) {
                warmEntityCache(missing)
            }
        }

        // Optimistically flip the cached state so the render reflects the tap before the
        // WebSocket subscription confirms. Required because `requestUpdate()` does not
        // reliably trigger the Wear tile framework to re-render.
        if (clicked.isNotEmpty()) {
            entityStates.computeIfPresent(clicked) { _, current -> applyOptimisticClick(current) }
        }

        // Freeze state at the moment the layout is built; the matching resource bundle must
        // use the same snapshot so every resource ID the layout references is produced.
        val snapshot = TileSnapshot.from(entityStates, entityIds)
        val resourcesVersion = "$TAG$tileId.${System.currentTimeMillis()}"
        snapshotStash.put(resourcesVersion, snapshot)

        Tile.Builder()
            .setResourcesVersion(resourcesVersion)
            .setTileTimeline(
                if (isRegistered) {
                    val showLabels = wearPrefsRepository.getShowShortcutText()
                    Timeline.fromLayoutElement(layout(entities, showLabels, snapshot))
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
            val showLabels = wearPrefsRepository.getShowShortcutText()
            val iconSize = if (showLabels) ICON_SIZE_SMALL else ICON_SIZE_FULL
            val density = requestParams.deviceConfiguration.screenDensity
            val iconSizePx = (iconSize * density).roundToInt()
            val entities = getEntities(requestParams.tileId)
            // Reuse the snapshot frozen in onTileRequest so the resource IDs here match what
            // the layout referenced. Falls back to current cache if the stash has been cleared.
            val snapshot = snapshotStash.get(requestParams.version)
                ?: TileSnapshot.from(entityStates, entities.map { it.entityId })

            Resources.Builder()
                .setVersion(requestParams.version)
                .apply {
                    entities.forEach { entity ->
                        val cachedEntity = snapshot.entityOf(entity.entityId)
                        val iconIIcon = if (cachedEntity != null) {
                            cachedEntity.getIcon(this@ShortcutsTile)
                        } else {
                            getIcon(entity.icon, entity.domain, this@ShortcutsTile)
                        }
                        addIdToImageMapping(
                            entity.resourceIdIn(snapshot),
                            buildIconResource(iconIIcon, iconSize, iconSizePx),
                        )
                    }
                }
                .build()
        }

    override fun onTileEnterEvent(requestParams: EventBuilders.TileEnterEvent) {
        serviceScope.launch {
            val entities = getEntities(requestParams.tileId)
            // onTileRequest warms the cache on cold start; enter is only for subscription
            // lifecycle. Always restart so the subscription reflects the current entity list.
            stopEntitySubscription()
            startEntitySubscription(
                serverManager = serverManager,
                entityIds = entities.map { it.entityId },
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
        snapshotStash.clear()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    private suspend fun getEntities(tileId: Int): List<SimplifiedEntity> {
        return wearPrefsRepository.getTileShortcutsAndSaveTileId(tileId).map { SimplifiedEntity(it) }
    }

    /**
     * Fetches state for [entityIds] in parallel and writes successful results into the cache.
     * Bounded by [CACHE_WARM_TIMEOUT_MS] so slow networks can't exceed the Wear tile render
     * timeout — entities whose fetch hasn't landed in time simply stay missing from the cache
     * and render with their domain-default icon.
     */
    private suspend fun warmEntityCache(entityIds: List<String>) {
        val repo = serverManager.integrationRepository()
        try {
            withTimeoutOrNull(CACHE_WARM_TIMEOUT_MS) {
                coroutineScope {
                    entityIds.map { id ->
                        async {
                            runCatching { repo.getEntity(id) }.getOrNull()?.let { entity ->
                                entityStates[entity.entityId] = entity
                            }
                        }
                    }.awaitAll()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to warm entity cache")
        }
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

    internal fun layout(entities: List<SimplifiedEntity>, showLabels: Boolean, snapshot: TileSnapshot): LayoutElement =
        Column.Builder().apply {
            if (entities.isEmpty()) {
                addContent(
                    LayoutElementBuilders.Text.Builder()
                        .setText(getString(commonR.string.shortcuts_tile_empty))
                        .build(),
                )
            } else {
                addContent(rowLayout(entities.subList(0, min(2, entities.size)), showLabels, snapshot))
                if (entities.size > 2) {
                    addContent(Spacer.Builder().setHeight(dp(SPACING)).build())
                    addContent(rowLayout(entities.subList(2, min(5, entities.size)), showLabels, snapshot))
                }
                if (entities.size > 5) {
                    addContent(Spacer.Builder().setHeight(dp(SPACING)).build())
                    addContent(rowLayout(entities.subList(5, min(7, entities.size)), showLabels, snapshot))
                }
            }
        }
            .build()

    private fun rowLayout(
        entities: List<SimplifiedEntity>,
        showLabels: Boolean,
        snapshot: TileSnapshot,
    ): LayoutElement = Row.Builder().apply {
        addContent(iconLayout(entities[0], showLabels, snapshot))
        entities.drop(1).forEach { entity ->
            addContent(Spacer.Builder().setWidth(dp(SPACING)).build())
            addContent(iconLayout(entity, showLabels, snapshot))
        }
    }
        .build()

    private fun iconLayout(entity: SimplifiedEntity, showLabels: Boolean, snapshot: TileSnapshot): LayoutElement =
        Box.Builder().apply {
            val iconSize = if (showLabels) ICON_SIZE_SMALL else ICON_SIZE_FULL
            val resourceId = entity.resourceIdIn(snapshot)
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

        /**
         * Entity cache shared across service instances. Populated by the WebSocket subscription
         * while a tile is visible, warmed on cold start by bounded parallel REST fetches, and
         * updated optimistically on tap. Read-only on the tile render path.
         */
        private val entityStates = ConcurrentHashMap<String, Entity>()

        /**
         * Snapshots of [entityStates] keyed by the resources version issued in `onTileRequest`.
         * Ensures the matching `onTileResourcesRequest` renders bitmaps for the same state the
         * layout referenced, even if the live cache mutated in between.
         */
        private val snapshotStash = SnapshotStash()

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
                    val flow = serverManager.integrationRepository().getEntityUpdates(entityIds) ?: return@launch
                    flow.collect { entity ->
                        entityStates[entity.entityId] = entity
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
