package io.homeassistant.companion.android.tiles

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.wear.tiles.ActionBuilders
import androidx.wear.tiles.ColorBuilders.argb
import androidx.wear.tiles.DimensionBuilders.dp
import androidx.wear.tiles.LayoutElementBuilders
import androidx.wear.tiles.LayoutElementBuilders.Box
import androidx.wear.tiles.LayoutElementBuilders.Column
import androidx.wear.tiles.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.tiles.LayoutElementBuilders.Layout
import androidx.wear.tiles.LayoutElementBuilders.LayoutElement
import androidx.wear.tiles.LayoutElementBuilders.Row
import androidx.wear.tiles.LayoutElementBuilders.Spacer
import androidx.wear.tiles.ModifiersBuilders
import androidx.wear.tiles.RequestBuilders.ResourcesRequest
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.ResourceBuilders
import androidx.wear.tiles.ResourceBuilders.Resources
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import androidx.wear.tiles.TimelineBuilders.Timeline
import androidx.wear.tiles.TimelineBuilders.TimelineEntry
import com.google.common.util.concurrent.ListenableFuture
import com.mikepenz.iconics.IconicsColor
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.utils.backgroundColor
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.data.SimplifiedEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.guava.future
import java.nio.ByteBuffer
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.roundToInt

// Dimensions (dp)
private const val CIRCLE_SIZE = 56f
private const val ICON_SIZE = 48f * 0.7071f // square that fits in 48dp circle
private const val SPACING = 8f

class ShortcutsTile : TileService() {
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    override fun onTileRequest(requestParams: TileRequest): ListenableFuture<Tile> =
        serviceScope.future {
            val state = requestParams.state
            if (state != null && state.lastClickableId.isNotEmpty()) {
                Intent().also { intent ->
                    intent.action = "io.homeassistant.companion.android.TILE_ACTION"
                    intent.putExtra("entity_id", state.lastClickableId)
                    intent.setPackage(packageName)
                    sendBroadcast(intent)
                }
            }

            val entities = getEntities()

            Tile.Builder()
                .setResourcesVersion(entities.toString())
                .setTimeline(
                    Timeline.Builder().addTimelineEntry(
                        TimelineEntry.Builder().setLayout(
                            Layout.Builder().setRoot(
                                layout(entities)
                            ).build()
                        ).build()
                    ).build()
                ).build()
        }

    override fun onResourcesRequest(requestParams: ResourcesRequest): ListenableFuture<Resources> =
        serviceScope.future {
            val density = requestParams.deviceParameters!!.screenDensity
            val iconSizePx = (ICON_SIZE * density).roundToInt()
            val entities = getEntities()

            Resources.Builder()
                .setVersion(entities.toString())
                .apply {
                    entities.map { entity ->
                        // Find icon name
                        val iconName: String = if (entity.icon.startsWith("mdi")) {
                            entity.icon.split(":")[1]
                        } else { // Default scene icon
                            when (entity.entityId.split(":")[1]) {
                                "input_boolean", "switch" -> "light_switch"
                                "light" -> "lightbulb"
                                "script" -> "script_text_outline"
                                "scene" -> "palette_outline"
                                else -> "cellphone"
                            }
                        }

                        // Create Bitmap from icon name
                        val iconBitmap = IconicsDrawable(this@ShortcutsTile, "cmd-$iconName").apply {
                            colorInt = Color.WHITE
                            sizeDp = ICON_SIZE.roundToInt()
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
                                    .build()
                            )
                            .build()
                    }.forEach { (id, imageResource) ->
                        addIdToImageMapping(id, imageResource)
                    }
                }
                .build()
        }

    override fun onDestroy() {
        super.onDestroy()
        // Cleans up the coroutine
        serviceJob.cancel()
    }

    private suspend fun getEntities(): List<SimplifiedEntity> {
        DaggerTilesComponent.builder()
            .appComponent((applicationContext as GraphComponentAccessor).appComponent)
            .build()
            .inject(this@ShortcutsTile)

        return integrationUseCase.getTileShortcuts().map { SimplifiedEntity(it) }
    }

    fun layout(entities: List<SimplifiedEntity>): LayoutElement = Column.Builder().apply {
        if (entities.isEmpty()) {
            addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(getString(R.string.shortcuts_tile_empty))
                    .build()
            )
        } else {
            addContent(rowLayout(entities.subList(0, min(2, entities.size))))
            if (entities.size > 2) {
                addContent(Spacer.Builder().setHeight(dp(SPACING)).build())
                addContent(rowLayout(entities.subList(2, min(5, entities.size))))
            }
            if (entities.size > 5) {
                addContent(Spacer.Builder().setHeight(dp(SPACING)).build())
                addContent(rowLayout(entities.subList(5, min(7, entities.size))))
            }
        }
    }
        .build()

    private fun rowLayout(entities: List<SimplifiedEntity>): LayoutElement = Row.Builder().apply {
        addContent(iconLayout(entities[0]))
        entities.drop(1).forEach { entity ->
            addContent(Spacer.Builder().setWidth(dp(SPACING)).build())
            addContent(iconLayout(entity))
        }
    }
        .build()

    private fun iconLayout(entity: SimplifiedEntity): LayoutElement = Box.Builder().apply {
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
                                .build()
                        )
                        .build()
                )
                // Make clickable and call activity
                .setClickable(
                    ModifiersBuilders.Clickable.Builder()
                        .setId(entity.entityId)
                        .setOnClick(
                            ActionBuilders.LoadAction.Builder().build()
                        )
                        .build()
                )
                .build()
        )
        addContent(
            // Add icon
            LayoutElementBuilders.Image.Builder()
                .setResourceId(entity.entityId)
                .setWidth(dp(ICON_SIZE))
                .setHeight(dp(ICON_SIZE))
                .build()
        )
    }
        .build()
}
