package io.homeassistant.companion.android.tiles

import androidx.core.content.ContextCompat
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.VERTICAL_ALIGN_CENTER
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.ResourceBuilders.ImageResource
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.tiles.RequestBuilders.ResourcesRequest
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.conversation.ConversationActivity
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.guava.future

@AndroidEntryPoint
class ConversationTile : TileService() {
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    @Inject
    lateinit var serverManager: ServerManager

    override fun onTileRequest(requestParams: TileRequest): ListenableFuture<Tile> = serviceScope.future {
        Tile.Builder()
            .setResourcesVersion("1")
            .setTileTimeline(
                if (serverManager.isRegistered()) {
                    Timeline.fromLayoutElement(boxLayout())
                } else {
                    loggedOutTimeline(
                        this@ConversationTile,
                        requestParams,
                        commonR.string.assist,
                        commonR.string.assist_log_in,
                    )
                },
            ).build()
    }

    override fun onTileResourcesRequest(requestParams: ResourcesRequest): ListenableFuture<Resources> =
        serviceScope.future {
            Resources.Builder()
                .setVersion("1")
                .addIdToImageMapping(
                    "image",
                    ImageResource.Builder()
                        .setAndroidResourceByResId(
                            ResourceBuilders.AndroidImageResourceByResId.Builder()
                                .setResourceId(
                                    io.homeassistant.companion.android.R.drawable.ic_comment_processing_outline,
                                )
                                .build(),
                        )
                        .build(),
                )
                .build()
        }

    override fun onDestroy() {
        super.onDestroy()
        // Cleans up the coroutine
        serviceJob.cancel()
    }

    private fun boxLayout(): LayoutElementBuilders.LayoutElement = LayoutElementBuilders.Box.Builder()
        .addContent(rowElement())
        .setHeight(dp(40f))
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
                                        .build(),
                                ).build(),
                        ).build(),
                )
                .setBackground(
                    ModifiersBuilders.Background.Builder()
                        .setColor(argb(ContextCompat.getColor(baseContext, R.color.colorAccent)))
                        .setCorner(
                            ModifiersBuilders.Corner.Builder()
                                .setRadius(dp(20f))
                                .build(),
                        )
                        .build(),
                )
                .setPadding(
                    ModifiersBuilders.Padding.Builder()
                        .setStart(dp(16f))
                        .setEnd(dp(24f))
                        .build(),
                )
                .build(),
        )
        .build()

    private fun tappableElement(): LayoutElementBuilders.LayoutElement = LayoutElementBuilders.Text.Builder()
        .setText(getString(R.string.assist))
        .setFontStyle(
            LayoutElementBuilders.FontStyle.Builder()
                .setSize(sp(24f))
                .build(),
        )
        .setModifiers(
            ModifiersBuilders.Modifiers.Builder()
                .setPadding(
                    ModifiersBuilders.Padding.Builder()
                        .setStart(dp(8f))
                        .build(),
                )
                .build(),
        )
        .build()

    private fun imageElement(): LayoutElementBuilders.LayoutElement = LayoutElementBuilders.Image.Builder()
        .setResourceId("image")
        .setWidth(dp(24f))
        .setHeight(dp(24f))
        .setModifiers(
            ModifiersBuilders.Modifiers.Builder()
                .setPadding(
                    ModifiersBuilders.Padding.Builder()
                        .setTop(dp(2f))
                        .build(),
                )
                .build(),
        )
        .build()

    private fun rowElement(): LayoutElementBuilders.LayoutElement = LayoutElementBuilders.Row.Builder()
        .addContent(imageElement())
        .addContent(tappableElement())
        .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
        .build()
}
