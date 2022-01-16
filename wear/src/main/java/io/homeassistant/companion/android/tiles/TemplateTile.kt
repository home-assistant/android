package io.homeassistant.companion.android.tiles

import androidx.wear.tiles.ActionBuilders
import androidx.wear.tiles.DimensionBuilders
import androidx.wear.tiles.DimensionBuilders.dp
import androidx.wear.tiles.LayoutElementBuilders
import androidx.wear.tiles.LayoutElementBuilders.Box
import androidx.wear.tiles.LayoutElementBuilders.Layout
import androidx.wear.tiles.LayoutElementBuilders.LayoutElement
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
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.guava.future
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class TemplateTile : TileService() {
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    override fun onTileRequest(requestParams: TileRequest): ListenableFuture<Tile> =
        serviceScope.future {
            val template = integrationUseCase.getTemplateTileContent()
            val renderedText = try {
                integrationUseCase.renderTemplate(template, mapOf())
            } catch (e: Exception) {
                getString(commonR.string.template_tile_error)
            }

            Tile.Builder()
                .setResourcesVersion("1")
                .setFreshnessIntervalMillis(
                    integrationUseCase.getTemplateTileRefreshInterval().toLong() * 1000
                )
                .setTimeline(
                    Timeline.Builder().addTimelineEntry(
                        TimelineEntry.Builder().setLayout(
                            Layout.Builder().setRoot(
                                layout(renderedText)
                            ).build()
                        ).build()
                    ).build()
                ).build()
        }

    override fun onResourcesRequest(requestParams: ResourcesRequest): ListenableFuture<Resources> =
        serviceScope.future {
            Resources.Builder()
                .setVersion("1")
                .addIdToImageMapping(
                    "refresh",
                    ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(
                            ResourceBuilders.AndroidImageResourceByResId.Builder()
                                .setResourceId(R.drawable.ic_refresh)
                                .build()
                        ).build()
                )
                .build()
        }

    override fun onDestroy() {
        super.onDestroy()
        // Cleans up the coroutine
        serviceJob.cancel()
    }

    fun layout(renderedText: String): LayoutElement = Box.Builder().apply {
        addContent(
            LayoutElementBuilders.Text.Builder()
                .setText(
                    if (renderedText.isEmpty()) {
                        getString(commonR.string.template_tile_empty)
                    } else {
                        renderedText
                    }
                )
                .setMaxLines(10)
                .build()
        )
        addContent(
            LayoutElementBuilders.Arc.Builder()
                .setAnchorAngle(
                    DimensionBuilders.DegreesProp.Builder()
                        .setValue(180f)
                        .build()
                )
                .addContent(
                    LayoutElementBuilders.ArcAdapter.Builder()
                        .setContent(
                            LayoutElementBuilders.Image.Builder()
                                .setResourceId("refresh")
                                .setWidth(dp(24f))
                                .setHeight(dp(24f))
                                .setModifiers(
                                    ModifiersBuilders.Modifiers.Builder()
                                        .setClickable(
                                            ModifiersBuilders.Clickable.Builder()
                                                .setOnClick(
                                                    ActionBuilders.LoadAction.Builder().build()
                                                )
                                                .build()
                                        )
                                        .build()
                                )
                                .build()
                        )
                        .setRotateContents(false)
                        .build()
                )
                .build()
        )
    }
        .build()
}
