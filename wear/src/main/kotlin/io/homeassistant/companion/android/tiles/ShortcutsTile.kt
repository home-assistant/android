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
import io.homeassistant.companion.android.common.data.prefs.WearPrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.data.SimplifiedEntity
import io.homeassistant.companion.android.util.getIcon
import java.nio.ByteBuffer
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

// Dimensions (dp)
private const val CIRCLE_SIZE = 56f
private const val ICON_SIZE_FULL = 48f * 0.7071f // square that fits in 48dp circle
private const val ICON_SIZE_SMALL = 40f * 0.7071f // square that fits in 48dp circle
private const val SPACING = 8f
private const val TEXT_SIZE = 8f
private const val TEXT_PADDING = 2f

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
        if (state.lastClickableId.isNotEmpty()) {
            Intent().also { intent ->
                intent.action = "io.homeassistant.companion.android.TILE_ACTION"
                intent.putExtra("entity_id", state.lastClickableId)
                intent.setPackage(packageName)
                sendBroadcast(intent)
            }
        }

        val tileId = requestParams.tileId
        val entities = getEntities(tileId)

        Tile.Builder()
            .setResourcesVersion(entities.toString())
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
            val showLabels = wearPrefsRepository.getShowShortcutText()
            val iconSize = if (showLabels) ICON_SIZE_SMALL else ICON_SIZE_FULL
            val density = requestParams.deviceConfiguration.screenDensity
            val iconSizePx = (iconSize * density).roundToInt()
            val entities = getEntities(requestParams.tileId)

            Resources.Builder()
                .setVersion(entities.toString())
                .apply {
                    entities.map { entity ->
                        // Find icon and create Bitmap
                        val iconIIcon = getIcon(
                            entity.icon,
                            entity.domain,
                            this@ShortcutsTile,
                        )
                        val iconBitmap = IconicsDrawable(this@ShortcutsTile, iconIIcon).apply {
                            colorInt = Color.WHITE
                            sizeDp = iconSize.roundToInt()
                            backgroundColor = IconicsColor.colorRes(R.color.colorOverlay)
                        }.toBitmap(iconSizePx, iconSizePx, Bitmap.Config.RGB_565)

                        // Make array of bitmap
                        val bitmapData = ByteBuffer.allocate(iconBitmap.byteCount).apply {
                            iconBitmap.copyPixelsToBuffer(this)
                        }.array()

                        // link the entity id to the bitmap data array
                        entity.entityId to ResourceBuilders.ImageResource.Builder()
                            .setInlineResource(
                                ResourceBuilders.InlineImageResource.Builder()
                                    .setData(bitmapData)
                                    .setWidthPx(iconSizePx)
                                    .setHeightPx(iconSizePx)
                                    .setFormat(ResourceBuilders.IMAGE_FORMAT_RGB_565)
                                    .build(),
                            )
                            .build()
                    }.forEach { (id, imageResource) ->
                        addIdToImageMapping(id, imageResource)
                    }
                }
                .build()
        }

    override fun onTileAddEvent(requestParams: EventBuilders.TileAddEvent): Unit = runBlocking {
        withContext(Dispatchers.IO) {
            /**
             * When the app is updated from an older version (which only supported a single Shortcut Tile),
             * and the user is adding a new Shortcuts Tile, we can't tell for sure if it's the 1st or 2nd Tile.
             * Even though we may have the shortcut list stored in the prefs, it doesn't guarantee that
             *   the tile was actually added to the Tiles carousel.
             * The [WearPrefsRepositoryImpl::getTileShortcutsAndSaveTileId] method will handle both of the following cases:
             * 1. There was no Tile added, but there were shortcuts stored in the prefs.
             *    In this case, the stored shortcuts will be associated to the new tileId.
             * 2. There was a single Tile added, and there were shortcuts stored in the prefs.
             *    If there was a Tile update since updating the app, the tileId will be already
             *    associated to the shortcuts, because it also calls [getTileShortcutsAndSaveTileId].
             *    If there was no Tile update yet, the new Tile will "steal" the shortcuts from the existing Tile,
             *    and the old Tile will behave as it is the new Tile. This is needed because
             *    we don't know if it's the 1st or 2nd Tile.
             */
            wearPrefsRepository.getTileShortcutsAndSaveTileId(requestParams.tileId)
        }
    }

    override fun onTileRemoveEvent(requestParams: EventBuilders.TileRemoveEvent): Unit = runBlocking {
        withContext(Dispatchers.IO) {
            wearPrefsRepository.removeTileShortcuts(requestParams.tileId)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cleans up the coroutine
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
        setWidth(dp(CIRCLE_SIZE))
        setHeight(dp(CIRCLE_SIZE))
        setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
        setModifiers(
            ModifiersBuilders.Modifiers.Builder()
                // Set circular background
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
                // Make clickable and call activity
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
            // Add icon
            LayoutElementBuilders.Image.Builder()
                .setResourceId(entity.entityId)
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
        fun requestUpdate(context: Context) {
            getUpdater(context).requestUpdate(ShortcutsTile::class.java)
        }
    }
}
