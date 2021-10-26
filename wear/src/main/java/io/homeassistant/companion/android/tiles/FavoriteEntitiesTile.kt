package io.homeassistant.companion.android.tiles

import androidx.core.content.ContextCompat
import androidx.wear.tiles.ActionBuilders
import androidx.wear.tiles.ColorBuilders.argb
import androidx.wear.tiles.DimensionBuilders.dp
import androidx.wear.tiles.LayoutElementBuilders.Box
import androidx.wear.tiles.LayoutElementBuilders.Column
import androidx.wear.tiles.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.tiles.LayoutElementBuilders.Row
import androidx.wear.tiles.LayoutElementBuilders.Spacer
import androidx.wear.tiles.LayoutElementBuilders.Text
import androidx.wear.tiles.LayoutElementBuilders.LayoutElement
import androidx.wear.tiles.LayoutElementBuilders.Layout
import androidx.wear.tiles.ModifiersBuilders
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.RequestBuilders.ResourcesRequest
import androidx.wear.tiles.ResourceBuilders.Resources
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import androidx.wear.tiles.TimelineBuilders.Timeline
import androidx.wear.tiles.TimelineBuilders.TimelineEntry
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.home.HomeActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.guava.future
import javax.inject.Inject

private const val RESOURCES_VERSION = "0.3"

// Dimensions (dp)
private const val CIRCLE_SIZE = 56f
private const val SPACING = 8f

class FavoriteEntitiesTile : TileService() {
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    override fun onTileRequest(requestParams: TileRequest): ListenableFuture<Tile> =
        serviceScope.future {
            DaggerTilesComponent.builder()
                .appComponent((applicationContext as GraphComponentAccessor).appComponent)
                .build()
                .inject(this@FavoriteEntitiesTile)
            val entities = integrationUseCase.getEntities()
                .sortedBy { it.entityId }
                .filter { it.entityId.split(".")[0] == "scene" }
            Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTimeline(Timeline.Builder().addTimelineEntry(
                    TimelineEntry.Builder().setLayout(
                        Layout.Builder().setRoot(
                            layout(entities)
                        ).build()
                    ).build()
                ).build()
                ).build()
        }

    override fun onResourcesRequest(requestParams: ResourcesRequest): ListenableFuture<Resources> =
        Futures.immediateFuture(
            Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .build()
        )

    override fun onDestroy() {
        super.onDestroy()
        // Cleans up the coroutine
        serviceJob.cancel()
    }

    fun layout(entities: List<Entity<Any>>): LayoutElement = Column.Builder()
        .addContent(
            Row.Builder()
                .addContent(iconLayout(entities[0]))
                .addContent(Spacer.Builder().setWidth(dp(SPACING)).build())
                .addContent(iconLayout(entities[1]))
                .build()
        )
        .addContent(
            Row.Builder()
                .addContent(iconLayout(entities[2]))
                .addContent(Spacer.Builder().setWidth(dp(SPACING)).build())
                .addContent(iconLayout(entities[3]))
                .addContent(Spacer.Builder().setWidth(dp(SPACING)).build())
                .addContent(iconLayout(entities[4]))
                .build()
        )
        .addContent(
            Row.Builder()
                .addContent(iconLayout(entities[5]))
                .addContent(Spacer.Builder().setWidth(dp(SPACING)).build())
                .addContent(iconLayout(entities[6]))
                .build()
        )
        .build()

    private fun iconLayout(entity: Entity<Any>): LayoutElement = Box.Builder().apply {
        val entityAttributes = entity.attributes as Map<String, String>
        val name = entityAttributes["friendly_name"]

        setWidth(dp(CIRCLE_SIZE))
        setHeight(dp(CIRCLE_SIZE))
        setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
        setModifiers(ModifiersBuilders.Modifiers.Builder()
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
            .setClickable(ModifiersBuilders.Clickable.Builder()
                .setOnClick(
                    ActionBuilders.LaunchAction.Builder()
                        .setAndroidActivity(
                            ActionBuilders.AndroidActivity.Builder()
                                .setClassName(HomeActivity::class.java.name)
                                .setPackageName(this@FavoriteEntitiesTile.packageName)
                                .build()
                        )
                        .build()
                )
                .build()
            )
            .build()
        )
        addContent(
            Text.Builder()
                .setText(name!!.substring(0,2))
                .build()
        )
    }
        .build()
}