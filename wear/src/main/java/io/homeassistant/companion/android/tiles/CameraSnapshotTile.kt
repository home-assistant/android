package io.homeassistant.companion.android.tiles

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.CONTENT_SCALE_MODE_FIT
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.ResourceBuilders.ImageResource
import androidx.wear.protolayout.ResourceBuilders.InlineImageResource
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.tiles.EventBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.wear.CameraSnapshotTile
import io.homeassistant.companion.android.util.UrlUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class CameraSnapshotTile : TileService() {

    companion object {
        private const val TAG = "CameraSnapshotTile"

        private const val RESOURCE_SNAPSHOT = "snapshot"
    }

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var okHttpClient: OkHttpClient

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<Tile> =
        serviceScope.future {
            val tileId = requestParams.tileId
            val tileConfig = AppDatabase.getInstance(this@CameraSnapshotTile)
                .cameraSnapshotTileDao()
                .get(tileId)

            Tile.Builder()
                .setResourcesVersion("$TAG$tileId.${System.currentTimeMillis()}")
                .setFreshnessIntervalMillis(
                    TimeUnit.SECONDS.toMillis(tileConfig?.refreshInterval ?: 3600L)
                )
                .setTileTimeline(
                    if (serverManager.isRegistered() && tileConfig != null) {
                        timeline(
                            requestParams.deviceConfiguration.screenWidthDp,
                            requestParams.deviceConfiguration.screenHeightDp
                        )
                    } else if (serverManager.isRegistered()) {
                        // TODO emptystate
                        timeline(
                            requestParams.deviceConfiguration.screenWidthDp,
                            requestParams.deviceConfiguration.screenHeightDp
                        )
                    } else {
                        loggedOutTimeline(
                            this@CameraSnapshotTile,
                            requestParams,
                            commonR.string.camera_snapshot,
                            commonR.string.camera_snapshot_tile_log_in
                        )
                    }
                )
                .build()
        }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<Resources> =
        serviceScope.future {
            var imageWidth = 0
            var imageHeight = 0
            val imageData = if (serverManager.isRegistered()) {
                val tileId = requestParams.tileId
                val tileConfig = AppDatabase.getInstance(this@CameraSnapshotTile)
                    .cameraSnapshotTileDao()
                    .get(tileId)

                try {
                    val entity = tileConfig?.entityId?.let {
                        serverManager.integrationRepository().getEntity(it)
                    }
                    val picture = entity?.attributes?.get("entity_picture")?.toString()
                    val url = UrlUtil.handle(serverManager.getServer()?.connection?.getUrl(), picture ?: "")
                    if (picture != null && url != null) {
                        var byteArray: ByteArray?
                        var bitmap: Bitmap? = null
                        withContext(Dispatchers.IO) {
                            val response = okHttpClient.newCall(Request.Builder().url(url).build()).execute()
                            byteArray = response.body?.byteStream()?.readBytes()
                            byteArray?.let {
                                bitmap = BitmapFactory.decodeByteArray(it, 0, it.size)
                            }
                            response.close()
                        }
                        imageWidth = bitmap?.width ?: 0
                        imageHeight = bitmap?.height ?: 0
                        bitmap = null
                        byteArray
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to fetch entity ${tileConfig?.entityId}", e)
                    null
                }
            } else {
                null
            }

            val builder = Resources.Builder()
                .setVersion(requestParams.version)
                .addIdToImageMapping(
                    RESOURCE_REFRESH,
                    ImageResource.Builder()
                        .setAndroidResourceByResId(
                            ResourceBuilders.AndroidImageResourceByResId.Builder()
                                .setResourceId(R.drawable.ic_refresh)
                                .build()
                        ).build()
                )
            if (imageData != null) {
                builder.addIdToImageMapping(
                    RESOURCE_SNAPSHOT,
                    ImageResource.Builder()
                        .setInlineResource(
                            InlineImageResource.Builder()
                                .setData(imageData)
                                .setWidthPx(imageWidth)
                                .setHeightPx(imageHeight)
                                .setFormat(ResourceBuilders.IMAGE_FORMAT_UNDEFINED)
                                .build()
                        )
                        .build()
                )
            }

            builder.build()
        }

    override fun onTileAddEvent(requestParams: EventBuilders.TileAddEvent) {
        serviceScope.launch {
            AppDatabase.getInstance(this@CameraSnapshotTile)
                .cameraSnapshotTileDao()
                .add(CameraSnapshotTile(id = requestParams.tileId))
        }
    }

    override fun onTileRemoveEvent(requestParams: EventBuilders.TileRemoveEvent) {
        serviceScope.launch {
            AppDatabase.getInstance(this@CameraSnapshotTile)
                .cameraSnapshotTileDao()
                .delete(requestParams.tileId)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun timeline(width: Int, height: Int): Timeline = Timeline.fromLayoutElement(
        LayoutElementBuilders.Box.Builder().apply {
            // Camera image
            addContent(
                LayoutElementBuilders.Image.Builder()
                    .setResourceId(RESOURCE_SNAPSHOT)
                    .setWidth(DimensionBuilders.dp(width.toFloat()))
                    .setHeight(DimensionBuilders.dp(height.toFloat()))
                    .setContentScaleMode(CONTENT_SCALE_MODE_FIT)
                    .build()
            )
            // Refresh button
            addContent(getRefreshButton())
            // Click: refresh
            setModifiers(getRefreshModifiers())
        }.build()
    )
}
