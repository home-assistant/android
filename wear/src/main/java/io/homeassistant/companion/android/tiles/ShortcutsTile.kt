package io.homeassistant.companion.android.tiles

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.wear.tiles.ActionBuilders
import androidx.wear.tiles.ColorBuilders.argb
import androidx.wear.tiles.DimensionBuilders.dp
import androidx.wear.tiles.DimensionBuilders.sp
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
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import com.mikepenz.iconics.utils.backgroundColor
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.data.SimplifiedEntity
import io.homeassistant.companion.android.util.getIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.guava.future
import java.nio.ByteBuffer
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.roundToInt
import io.homeassistant.companion.android.common.R as commonR

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
            val showLabels = integrationUseCase.getShowShortcutText()

            Tile.Builder()
                .setResourcesVersion(entities.toString())
                .setTimeline(
                    Timeline.Builder().addTimelineEntry(
                        TimelineEntry.Builder().setLayout(
                            Layout.Builder().setRoot(
                                layout(entities, showLabels)
                            ).build()
                        ).build()
                    ).build()
                ).build()
        }

    override fun onResourcesRequest(requestParams: ResourcesRequest): ListenableFuture<Resources> =
        serviceScope.future {
            val showLabels = integrationUseCase.getShowShortcutText()
            val iconSize = if (showLabels) ICON_SIZE_SMALL else ICON_SIZE_FULL
            val density = requestParams.deviceParameters!!.screenDensity
            val iconSizePx = (iconSize * density).roundToInt()
            val entities = getEntities()

            Resources.Builder()
                .setVersion(entities.toString())
                .apply {
                    entities.map { entity ->
                        // Find icon and create Bitmap
                        val iconIIcon = getIcon(
                            entity.icon,
                            entity.entityId.split("")[0],
                            this@ShortcutsTile
                        ) ?: CommunityMaterial.Icon.cmd_cellphone
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
        return integrationUseCase.getTileShortcuts().map { SimplifiedEntity(it) }
    }

    fun layout(entities: List<SimplifiedEntity>, showLabels: Boolean): LayoutElement = Column.Builder().apply {
        if (entities.isEmpty()) {
            addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(getString(commonR.string.shortcuts_tile_empty))
                    .build()
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
                .setWidth(dp(iconSize))
                .setHeight(dp(iconSize))
                .build()
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
                                    .build()
                            )
                            .build()
                    )
                    .setModifiers(
                        ModifiersBuilders.Modifiers.Builder()
                            .setPadding(
                                ModifiersBuilders.Padding.Builder()
                                    .setAll(dp(TEXT_PADDING))
                                    .build()
                            ).build()
                    )
                    .build()
            )
        }
    }
        .build()
}
