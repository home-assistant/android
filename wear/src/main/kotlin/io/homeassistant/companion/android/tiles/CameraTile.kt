package io.homeassistant.companion.android.tiles

import android.graphics.Bitmap
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
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Precision
import coil3.size.Scale
import coil3.toBitmap
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.entityPicturePath
import io.homeassistant.companion.android.common.data.prefs.WearPrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.UrlState
import io.homeassistant.companion.android.database.wear.CameraTile
import io.homeassistant.companion.android.database.wear.CameraTileDao
import io.homeassistant.companion.android.home.HomeActivity
import io.homeassistant.companion.android.util.UrlUtil
import java.io.ByteArrayOutputStream
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber

@AndroidEntryPoint
class CameraTile : TileService() {

    companion object {
        private const val TAG = "CameraTile"

        const val DEFAULT_REFRESH_INTERVAL = 3600L // 1 hour, matching phone widget

        private const val RESOURCE_SNAPSHOT = "snapshot"
        private const val SNAPSHOT_JPEG_QUALITY = 100
    }

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var wearPrefsRepository: WearPrefsRepository

    @Inject
    lateinit var cameraTileDao: CameraTileDao

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<Tile> =
        serviceScope.future {
            val tileId = requestParams.tileId
            val tileConfig = cameraTileDao.get(tileId)

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
            val snapshot = if (serverManager.isRegistered()) loadSnapshot(requestParams) else null

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
            if (snapshot != null) {
                builder.addIdToImageMapping(
                    RESOURCE_SNAPSHOT,
                    ImageResource.Builder()
                        .setInlineResource(
                            InlineImageResource.Builder()
                                .setData(snapshot.data)
                                .setWidthPx(snapshot.width)
                                .setHeightPx(snapshot.height)
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
            if (cameraTileDao.get(requestParams.tileId) == null) {
                cameraTileDao.add(CameraTile(id = requestParams.tileId))
            } // else already existing, don't overwrite existing tile data
        }
    }

    override fun onTileRemoveEvent(requestParams: EventBuilders.TileRemoveEvent) = runBlocking {
        withContext(Dispatchers.IO) {
            cameraTileDao.delete(requestParams.tileId)
        }
    }

    override fun onRecentInteractionEventsAsync(
        events: List<EventBuilders.TileInteractionEvent>,
    ): ListenableFuture<Void?> = serviceScope.future {
        requestUpdateOnEnter(events) { tileId ->
            (cameraTileDao.get(tileId)?.refreshInterval ?: 0) >= 1
        }
        null
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private suspend fun loadSnapshot(requestParams: RequestBuilders.ResourcesRequest): Snapshot? {
        val tileConfig = cameraTileDao.get(requestParams.tileId)
        return try {
            val entity = tileConfig?.entityId?.let {
                serverManager.integrationRepository().getEntity(it)
            }
            val picture = entity?.entityPicturePath()
            val urlState = serverManager.connectionStateProvider().urlFlow().first()
            val baseUrl = if (urlState is UrlState.HasUrl) {
                urlState.url
            } else {
                null
            }
            val url = UrlUtil.handle(baseUrl, picture ?: "")
            if (picture != null && url != null) {
                val deviceConfiguration = requestParams.deviceConfiguration
                downloadSnapshot(
                    url = url,
                    maxWidth = (deviceConfiguration.screenWidthDp * deviceConfiguration.screenDensity).toInt(),
                    maxHeight = (deviceConfiguration.screenHeightDp * deviceConfiguration.screenDensity).toInt(),
                )
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Unable to fetch entity ${tileConfig?.entityId}")
            null
        }
    }

    private suspend fun downloadSnapshot(url: URL, maxWidth: Int, maxHeight: Int): Snapshot? {
        val request = ImageRequest.Builder(this)
            .data(url.toString())
            // The bitmap is compressed to be sent to the tile renderer, which needs it software-backed
            .allowHardware(false)
            // The snapshot URL is stable while its content changes, always fetch it
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            // Downscale snapshots larger than the screen, without upscaling smaller ones
            .size(maxWidth, maxHeight)
            .scale(Scale.FIT)
            .precision(Precision.INEXACT)
            .build()
        return when (val result = imageLoader.execute(request)) {
            is SuccessResult -> result.image.toBitmap().toSnapshot()

            is ErrorResult -> {
                Timber.e(result.throwable, "Unable to download camera snapshot")
                null
            }
        }
    }

    private fun Bitmap.toSnapshot(): Snapshot = ByteArrayOutputStream().use { stream ->
        compress(Bitmap.CompressFormat.JPEG, SNAPSHOT_JPEG_QUALITY, stream)
        Snapshot(data = stream.toByteArray(), width = width, height = height)
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

/** A camera snapshot encoded as JPEG along with its size in pixels. */
private class Snapshot(val data: ByteArray, val width: Int, val height: Int)
