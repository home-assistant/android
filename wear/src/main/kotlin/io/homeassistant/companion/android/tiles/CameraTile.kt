package io.homeassistant.companion.android.tiles

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
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
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.prefs.WearPrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.wear.CameraTile
import io.homeassistant.companion.android.home.HomeActivity
import io.homeassistant.companion.android.util.UrlUtil
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

@AndroidEntryPoint
class CameraTile : TileService() {

    companion object {
        private const val TAG = "CameraTile"

        const val DEFAULT_REFRESH_INTERVAL = 3600L // 1 hour, matching phone widget

        private const val RESOURCE_SNAPSHOT = "snapshot"
    }

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var wearPrefsRepository: WearPrefsRepository

    @Inject
    lateinit var okHttpClient: OkHttpClient

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<Tile> =
        serviceScope.future {
            val tileId = requestParams.tileId
            val tileConfig = AppDatabase.getInstance(this@CameraTile)
                .cameraTileDao()
                .get(tileId)

            if (requestParams.currentState.lastClickableId == MODIFIER_CLICK_REFRESH) {
                if (wearPrefsRepository.getWearHapticFeedback()) hapticClick(applicationContext)
            }
            val freshness = when {
                (tileConfig?.refreshInterval != null && tileConfig.refreshInterval!! <= 1) -> 0
                tileConfig?.refreshInterval != null -> tileConfig.refreshInterval!!
                else -> DEFAULT_REFRESH_INTERVAL
            }

            Tile.Builder()
                .setResourcesVersion("$TAG$tileId.${System.currentTimeMillis()}")
                .setFreshnessIntervalMillis(TimeUnit.SECONDS.toMillis(freshness))
                .setTileTimeline(
                    if (serverManager.isRegistered()) {
                        if (tileConfig?.entityId.isNullOrBlank()) {
                            getNotConfiguredTimeline(
                                this@CameraTile,
                                requestParams,
                                commonR.string.camera_tile_no_entity_yet,
                                HomeActivity.Companion.LaunchMode.CameraTile,
                            )
                        } else {
                            timeline(
                                requestParams.deviceConfiguration.screenWidthDp,
                                requestParams.deviceConfiguration.screenHeightDp,
                            )
                        }
                    } else {
                        loggedOutTimeline(
                            this@CameraTile,
                            requestParams,
                            commonR.string.camera,
                            commonR.string.camera_tile_log_in,
                        )
                    },
                )
                .build()
        }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<Resources> =
        serviceScope.future {
            var imageWidth = 0
            var imageHeight = 0
            val imageData = if (serverManager.isRegistered()) {
                val tileId = requestParams.tileId
                val tileConfig = AppDatabase.getInstance(this@CameraTile)
                    .cameraTileDao()
                    .get(tileId)

                try {
                    val entity = tileConfig?.entityId?.let {
                        serverManager.integrationRepository().getEntity(it)
                    }
                    val picture = entity?.attributes?.get("entity_picture")?.toString()
                    val url = UrlUtil.handle(serverManager.getServer()?.connection?.getUrl(), picture ?: "")
                    if (picture != null && url != null) {
                        var byteArray: ByteArray?
                        val maxWidth =
                            requestParams.deviceConfiguration.screenWidthDp *
                                requestParams.deviceConfiguration.screenDensity
                        val maxHeight =
                            requestParams.deviceConfiguration.screenHeightDp *
                                requestParams.deviceConfiguration.screenDensity
                        withContext(Dispatchers.IO) {
                            val response = okHttpClient.newCall(Request.Builder().url(url).build()).execute()
                            byteArray = response.body.byteStream().readBytes()
                            var bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
                            if (bitmap.width > maxWidth || bitmap.height > maxHeight) {
                                Timber.d(
                                    "Scaling camera snapshot to fit screen (${bitmap.width}x${bitmap.height} to ${maxWidth.toInt()}x${maxHeight.toInt()} max)",
                                )
                                val currentRatio = (bitmap.width.toFloat() / bitmap.height.toFloat())
                                val screenRatio = (
                                    requestParams.deviceConfiguration.screenWidthDp.toFloat() /
                                        requestParams.deviceConfiguration.screenHeightDp.toFloat()
                                    )
                                imageWidth = maxWidth.toInt()
                                imageHeight = maxHeight.toInt()
                                if (currentRatio > screenRatio) {
                                    imageWidth = (maxHeight * currentRatio).toInt()
                                } else {
                                    imageHeight = (maxWidth / currentRatio).toInt()
                                }
                                bitmap = bitmap.scale(imageWidth, imageHeight)
                                ByteArrayOutputStream().use { stream ->
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                                    byteArray = stream.toByteArray()
                                }
                            } else {
                                imageWidth = bitmap.width
                                imageHeight = bitmap.height
                            }
                            response.close()
                        }
                        byteArray
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Unable to fetch entity ${tileConfig?.entityId}")
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
                                .build(),
                        ).build(),
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
                                .build(),
                        )
                        .build(),
                )
            }

            builder.build()
        }

    override fun onTileAddEvent(requestParams: EventBuilders.TileAddEvent) = runBlocking {
        withContext(Dispatchers.IO) {
            val dao = AppDatabase.getInstance(this@CameraTile).cameraTileDao()
            if (dao.get(requestParams.tileId) == null) {
                dao.add(CameraTile(id = requestParams.tileId))
            } // else already existing, don't overwrite existing tile data
        }
    }

    override fun onTileRemoveEvent(requestParams: EventBuilders.TileRemoveEvent) = runBlocking {
        withContext(Dispatchers.IO) {
            AppDatabase.getInstance(this@CameraTile)
                .cameraTileDao()
                .delete(requestParams.tileId)
        }
    }

    override fun onTileEnterEvent(requestParams: EventBuilders.TileEnterEvent) {
        serviceScope.launch {
            val tileId = requestParams.tileId
            val tileConfig = AppDatabase.getInstance(this@CameraTile)
                .cameraTileDao()
                .get(tileId)
            tileConfig?.refreshInterval?.let {
                if (it >= 1) {
                    try {
                        getUpdater(this@CameraTile)
                            .requestUpdate(io.homeassistant.companion.android.tiles.CameraTile::class.java)
                    } catch (e: Exception) {
                        Timber.w(e, "Unable to request tile update on enter")
                    }
                }
            }
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
                    .build(),
            )
            // Refresh button
            addContent(getRefreshButton())
            // Click: refresh
            setModifiers(getRefreshModifiers())
        }.build(),
    )
}
