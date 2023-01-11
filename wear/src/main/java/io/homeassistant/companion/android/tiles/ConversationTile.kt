package io.homeassistant.companion.android.tiles

import androidx.core.content.ContextCompat
import androidx.wear.tiles.ActionBuilders
import androidx.wear.tiles.ColorBuilders.argb
import androidx.wear.tiles.DimensionBuilders.dp
import androidx.wear.tiles.DimensionBuilders.sp
import androidx.wear.tiles.LayoutElementBuilders
import androidx.wear.tiles.LayoutElementBuilders.VERTICAL_ALIGN_CENTER
import androidx.wear.tiles.ModifiersBuilders
import androidx.wear.tiles.RequestBuilders.ResourcesRequest
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.ResourceBuilders
import androidx.wear.tiles.ResourceBuilders.ImageResource
import androidx.wear.tiles.ResourceBuilders.Resources
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import androidx.wear.tiles.TimelineBuilders.Timeline
import androidx.wear.tiles.TimelineBuilders.TimelineEntry
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.conversation.ConversationActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.guava.future

@AndroidEntryPoint
class ConversationTile : TileService() {
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onTileRequest(requestParams: TileRequest): ListenableFuture<Tile> =
        serviceScope.future {
            Tile.Builder()
                .setResourcesVersion("1")
                .setTimeline(
                    Timeline.Builder().addTimelineEntry(
                        TimelineEntry.Builder().setLayout(
                            LayoutElementBuilders.Layout.Builder().setRoot(
                                boxLayout()
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
                    "image",
                    ImageResource.Builder()
                        .setAndroidResourceByResId(
                            ResourceBuilders.AndroidImageResourceByResId.Builder()
                                .setResourceId(io.homeassistant.companion.android.R.drawable.ic_comment_processing_outline)
                                .build()
                        )
                        .build()
                )
                .build()
        }

    override fun onDestroy() {
        super.onDestroy()
        // Cleans up the coroutine
        serviceJob.cancel()
    }

    private fun boxLayout(): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Box.Builder()
            .addContent(rowElement())
            .setHeight(dp(50f))
            .setWidth(dp(140f))
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(
                        ModifiersBuilders.Clickable.Builder()
                            .setId("conversation")
                            .setOnClick(
                                ActionBuilders.LaunchAction.Builder()
                                    .setAndroidActivity(
                                        ActionBuilders.AndroidActivity.Builder()
                                            .setClassName(ConversationActivity::class.java.name)
                                            .setPackageName(this.packageName)
                                            .build()
                                    ).build()
                            ).build()
                    )
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(ContextCompat.getColor(baseContext, R.color.colorAccent)))
                            .setCorner(
                                ModifiersBuilders.Corner.Builder()
                                    .setRadius(dp(10f))
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

    private fun tappableElement(): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Text.Builder()
            .setText(getString(R.string.assist))
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(30f))
                    .build()
            )
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setStart(dp(4f))
                            .build()
                    )
                    .build()
            )
            .build()

    private fun imageElement(): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Image.Builder()
            .setResourceId("image")
            .setWidth(dp(30f))
            .setHeight(dp(30f))
            .build()

    private fun rowElement(): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Row.Builder()
            .addContent(imageElement())
            .addContent(tappableElement())
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .build()
}
