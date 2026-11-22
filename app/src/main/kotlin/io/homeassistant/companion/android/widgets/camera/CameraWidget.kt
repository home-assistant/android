package io.homeassistant.companion.android.widgets.camera

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.os.PowerManager
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import androidx.core.content.getSystemService
import androidx.core.os.BundleCompat
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Precision
import coil3.size.Size
import coil3.toBitmap
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.integration.EntityFilter
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.IMAGE_DOMAIN
import io.homeassistant.companion.android.common.data.integration.entityPicturePath
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.UrlState
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CompressedStateChangedEvent
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.common.util.STATE_UNAVAILABLE
import io.homeassistant.companion.android.common.util.STATE_UNKNOWN
import io.homeassistant.companion.android.database.widget.CameraWidgetDao
import io.homeassistant.companion.android.database.widget.CameraWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetTapAction
import io.homeassistant.companion.android.frontend.navigation.FrontendTarget
import io.homeassistant.companion.android.launch.intentLaunchWithNavigateTo
import io.homeassistant.companion.android.util.hasActiveConnection
import io.homeassistant.companion.android.widgets.ACTION_APPWIDGET_CREATED
import io.homeassistant.companion.android.widgets.BaseWidgetProvider.Companion.UPDATE_WIDGETS
import io.homeassistant.companion.android.widgets.EXTRA_WIDGET_ENTITY
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

@AndroidEntryPoint
class CameraWidget @VisibleForTesting internal constructor(private val widgetScope: CoroutineScope) :
    AppWidgetProvider() {

    constructor() : this(defaultWidgetScope)

    companion object {
        internal const val UPDATE_IMAGE =
            "io.homeassistant.companion.android.widgets.camera.CameraWidget.UPDATE_IMAGE"
        private var lastIntent = ""
        private val defaultWidgetScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        // Looks up the image widgets to watch, only accessed on the main thread
        private var imageEntitiesJob: Job? = null

        // The watch of each image widget by widget id while the screen is on, only accessed on the main thread
        private val imageWatches = mutableMapOf<Int, ImageWatch>()

        // The entity state each widget was last published with. State alone is insufficient because
        // a widget can be reconfigured to another entity with the same last_changed timestamp.
        private val drawnEntityStates = ConcurrentHashMap<Int, DrawnEntityState>()
        private val widgetUpdateLocks = ConcurrentHashMap<Int, WidgetUpdateLock>()
    }

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var cameraWidgetDao: CameraWidgetDao

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // There may be multiple widgets active, so update all of them
        appWidgetIds.forEach { appWidgetId ->
            widgetScope.launch {
                updateAppWidget(
                    context,
                    appWidgetId,
                    appWidgetManager,
                )
            }
        }
    }

    private suspend fun updateAppWidget(
        context: Context,
        appWidgetId: Int,
        appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context),
        reason: ImageUpdateReason = ImageUpdateReason.FullRefresh,
        watchedIdentity: DrawnEntityIdentity? = null,
    ) {
        if (!context.hasActiveConnection()) {
            Timber.d("Skipping widget update since network connection is not active")
            return
        }
        withWidgetUpdateLock(appWidgetId) {
            val widget = cameraWidgetDao.get(appWidgetId)
            if (watchedIdentity != null && widget?.identity() != watchedIdentity) return@withWidgetUpdateLock
            if (
                reason == ImageUpdateReason.RetryOnlyIfUncached &&
                drawnEntityStates[appWidgetId]?.identity == widget?.identity()
            ) {
                return@withWidgetUpdateLock
            }
            val result = getWidgetRemoteViews(context, appWidgetId, appWidgetManager, widget, reason)
                ?: return@withWidgetUpdateLock
            try {
                appWidgetManager.updateAppWidget(appWidgetId, result.views)
            } catch (e: Exception) {
                Timber.e(e, "Unable to publish camera widget update")
                return@withWidgetUpdateLock
            }
            if (result.drawnState != null) {
                drawnEntityStates[appWidgetId] = result.drawnState
            } else {
                drawnEntityStates.remove(appWidgetId)
            }
        }
    }

    private suspend fun withWidgetUpdateLock(appWidgetId: Int, action: suspend () -> Unit) {
        val lock = checkNotNull(
            widgetUpdateLocks.compute(appWidgetId) { _, existing ->
                (existing ?: WidgetUpdateLock()).also { it.users++ }
            },
        )
        try {
            lock.mutex.withLock { action() }
        } finally {
            widgetUpdateLocks.compute(appWidgetId) { _, current ->
                check(current === lock) { "Widget update lock was replaced while in use" }
                lock.users--
                if (lock.users == 0) null else lock
            }
        }
    }

    private suspend fun updateAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
        val systemWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, CameraWidget::class.java))
        val dbWidgetList = cameraWidgetDao.getAll()

        val invalidWidgetIds = dbWidgetList
            .filter { !systemWidgetIds.contains(it.id) }
            .map { it.id }
        if (invalidWidgetIds.isNotEmpty()) {
            Timber.i("Found widgets $invalidWidgetIds in database, but not in AppWidgetManager - sending onDeleted")
            withContext(Dispatchers.Main) { onDeleted(context, invalidWidgetIds.toIntArray()) }
        }

        val cameraWidgetList = dbWidgetList.filter { systemWidgetIds.contains(it.id) }
        if (cameraWidgetList.isNotEmpty()) {
            Timber.d("Updating all widgets")
            for (item in cameraWidgetList) {
                updateAppWidget(context, item.id, appWidgetManager)
            }
        }
    }

    private suspend fun getWidgetRemoteViews(
        context: Context,
        appWidgetId: Int,
        appWidgetManager: AppWidgetManager,
        widget: CameraWidgetEntity?,
        reason: ImageUpdateReason,
    ): WidgetRemoteViews? = CameraWidgetRemoteViewsFactory(
        context,
        appWidgetManager,
        serverManager,
        ::getWidgetBitmapSize,
    )
        .create(appWidgetId, widget, reason, drawnEntityStates[appWidgetId])

    /**
     * Redraws the widgets showing an image entity when the image changes, until
     * [stopWatchingImageEntities]. Camera entities keep only the periodic update and the tap to
     * refresh, because their state does not change with the picture.
     */
    @MainThread
    private fun watchImageEntities(context: Context) {
        imageEntitiesJob?.cancel()
        imageEntitiesJob = widgetScope.launch {
            if (!serverManager.isRegistered()) return@launch
            val imageWidgets = cameraWidgetDao.getAll().filter {
                it.entityId.substringBefore('.') == IMAGE_DOMAIN && serverManager.getServer(it.serverId) != null
            }
            withContext(Dispatchers.Main) {
                // A lookup finishing after the screen turned off must not start watches
                if (context.getSystemService<PowerManager>()?.isInteractive != true) return@withContext
                // Keep the unchanged watches: the websocket closes when its last subscription stops,
                // which would drop a subscription started at the same time. Restart the ones that ended.
                val widgetsById = imageWidgets.associateBy { it.id }
                imageWatches.filter { (id, watch) -> !watch.isFor(widgetsById[id]) || !watch.job.isActive }.keys
                    .forEach { imageWatches.remove(it)?.job?.cancel() }
                imageWidgets.filter {
                    it.id !in imageWatches
                }.forEach { widget -> startWatchingImageEntity(context, widget) }
            }
        }
    }

    private fun startWatchingImageEntity(context: Context, widget: CameraWidgetEntity) {
        val identity = widget.identity()
        val job = widgetScope.launch {
            val flow = serverManager.webSocketRepository(widget.serverId)
                .getCompressedStateAndChanges(EntityFilter(entityIds = setOf(widget.entityId)))
                ?.transform { event -> event.toImageUpdateReason(widget.entityId)?.let { emit(it) } }
            if (flow != null) {
                coroutineScope {
                    val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                        flow.collect { reason ->
                            updateAppWidget(context, widget.id, reason = reason, watchedIdentity = identity)
                        }
                    }
                    updateAppWidget(
                        context,
                        widget.id,
                        reason = ImageUpdateReason.CheckCurrentState,
                        watchedIdentity = identity,
                    )
                    collector.join()
                }
            }
        }
        imageWatches[widget.id] = ImageWatch(widget.serverId, widget.entityId, job)
    }

    /** Stops what [watchImageEntities] started, when the screen turns off. */
    @MainThread
    private fun stopWatchingImageEntities() {
        imageEntitiesJob?.cancel()
        imageEntitiesJob = null
        imageWatches.values.forEach { it.job.cancel() }
        imageWatches.clear()
    }

    override fun onReceive(context: Context, intent: Intent) {
        lastIntent = intent.action.toString()
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)

        Timber.d(
            "Broadcast received: " + System.lineSeparator() +
                "Broadcast action: " + lastIntent + System.lineSeparator() +
                "AppWidgetId: " + appWidgetId,
        )

        super.onReceive(context, intent)
        when (lastIntent) {
            UPDATE_IMAGE -> widgetScope.launch { updateAppWidget(context, appWidgetId) }
            UPDATE_WIDGETS -> widgetScope.launch {
                updateAllWidgets(context)
                withContext(Dispatchers.Main) { watchImageEntities(context) }
            }

            Intent.ACTION_SCREEN_ON -> watchImageEntities(context)

            Intent.ACTION_SCREEN_OFF -> stopWatchingImageEntities()

            ACTION_APPWIDGET_CREATED -> {
                widgetScope.launch {
                    if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                        FailFast.fail { "Missing appWidgetId in intent to add widget in DAO" }
                    } else {
                        val entity = intent.extras?.let {
                            BundleCompat.getSerializable(
                                it,
                                EXTRA_WIDGET_ENTITY,
                                CameraWidgetEntity::class.java,
                            )
                        }
                        entity?.let {
                            cameraWidgetDao.add(entity.copyWithWidgetId(appWidgetId))
                        } ?: FailFast.fail { "Missing $EXTRA_WIDGET_ENTITY or it's of the wrong type in intent." }
                    }
                    updateAllWidgets(context)
                    withContext(Dispatchers.Main) { watchImageEntities(context) }
                }
            }
        }
    }

    @MainThread
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> imageWatches.remove(id)?.job?.cancel() }
        widgetScope.launch {
            appWidgetIds.forEach { appWidgetId ->
                withWidgetUpdateLock(appWidgetId) {
                    cameraWidgetDao.deleteAll(intArrayOf(appWidgetId))
                    drawnEntityStates.remove(appWidgetId)
                }
            }
        }
    }

    /**
     * Returns a [Size] based on the widget's allocated dimensions, capped to stay within the
     * RemoteViews bitmap memory limit. Falls back to the full screen width and height when the
     * widget manager does not report a size.
     *
     * The system computes the limit as `6 * screenWidth * screenHeight`
     * (1.5× screen area × 4 bytes/pixel) in `AppWidgetServiceImpl.computeMaximumWidgetBitmapMemory`.
     * https://cs.android.com/android/platform/superproject/+/8b289e3d7cf7fd9242870758a894c6e9b4c3e655:frameworks/base/services/appwidget/java/com/android/server/appwidget/AppWidgetServiceImpl.java;l=513
     * There is no public API for this, so we replicate the formula and keep the camera bitmap
     * under roughly 90% of that limit to leave headroom for other bitmap work in the same
     * RemoteViews.
     */
    private fun getWidgetBitmapSize(appWidgetManager: AppWidgetManager, appWidgetId: Int): Size {
        val res = Resources.getSystem()
        val screenWidth = res.displayMetrics.widthPixels
        val screenHeight = res.displayMetrics.heightPixels
        // System limit = 1.5 × screen area × 4 bytes/pixel (ARGB_8888).
        // Bytes-per-pixel cancels out when converting to pixels: 1.5 * w * h * 4 * 0.9 / 4 = 1.35 * w * h.
        // The 0.9 factor keeps a 10% margin for RemoteViews overhead.
        val maxPixels = (1.35 * screenWidth * screenHeight).toInt()

        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
        val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)

        val widthPx: Int
        val heightPx: Int
        if (widthDp <= 0) {
            widthPx = screenWidth
            heightPx = screenHeight
        } else {
            val density = res.displayMetrics.density
            widthPx = (widthDp * density).toInt()
            heightPx = if (heightDp > 0) (heightDp * density).toInt() else 0
        }

        if (heightPx <= 0) {
            // Height unknown, derive a max height from the pixel budget so the
            // bitmap stays within limits even if the source image is unusually tall.
            val cappedWidth = minOf(widthPx, maxPixels / maxOf(screenHeight, 1)).coerceAtLeast(1)
            val maxHeight = (maxPixels / cappedWidth).coerceAtLeast(1)
            return Size(cappedWidth, maxHeight)
        }

        // Scale down proportionally if the bitmap would exceed the safe pixel budget
        return if (widthPx.toLong() * heightPx > maxPixels) {
            val scale = sqrt(maxPixels.toDouble() / (widthPx.toLong() * heightPx)).toFloat()
            Size((widthPx * scale).toInt().coerceAtLeast(1), (heightPx * scale).toInt().coerceAtLeast(1))
        } else {
            Size(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1))
        }
    }
}

private data class DrawnEntityIdentity(val serverId: Int, val entityId: String)

private data class DrawnEntityState(val identity: DrawnEntityIdentity, val state: String)

private sealed interface ImageUpdateReason {
    data object FullRefresh : ImageUpdateReason
    data object CheckCurrentState : ImageUpdateReason
    data object RetryOnlyIfUncached : ImageUpdateReason
}

private data class WidgetRemoteViews(val views: RemoteViews, val drawnState: DrawnEntityState?)

private class CameraWidgetRemoteViewsFactory(
    private val context: Context,
    private val appWidgetManager: AppWidgetManager,
    private val serverManager: ServerManager,
    private val bitmapSize: (AppWidgetManager, Int) -> Size,
) {
    suspend fun create(
        appWidgetId: Int,
        widget: CameraWidgetEntity?,
        reason: ImageUpdateReason,
        cachedState: DrawnEntityState?,
    ): WidgetRemoteViews? {
        val shouldRender = widget != null || reason == ImageUpdateReason.FullRefresh
        val metadata = widget?.let { getImageMetadata(it) } ?: CameraImageMetadata()
        val identity = widget?.identity()
        val currentState = if (identity != null && metadata.state != null) {
            DrawnEntityState(identity, metadata.state)
        } else {
            null
        }
        val alreadyCurrent = reason == ImageUpdateReason.CheckCurrentState &&
            currentState != null &&
            cachedState == currentState
        val missingState = reason != ImageUpdateReason.FullRefresh && !metadata.hasUsableState
        return if (!shouldRender || alreadyCurrent || missingState) {
            null
        } else {
            val bitmap = metadata.url?.let { fetchBitmap(it, appWidgetId) }
            val views = createRemoteViews(appWidgetId, widget, metadata, bitmap)
            val drawnState = if (bitmap != null) currentState else null
            WidgetRemoteViews(views, drawnState)
        }
    }

    private suspend fun getImageMetadata(widget: CameraWidgetEntity): CameraImageMetadata {
        if (serverManager.getServer(widget.serverId) == null) {
            Timber.w("Widget is attached to a server that has been removed")
            return CameraImageMetadata(error = true)
        }
        return try {
            val entity = serverManager.integrationRepository(widget.serverId).getEntity(widget.entityId)
            val urlState = serverManager.connectionStateProvider(widget.serverId).urlFlow().first()
            check(urlState is UrlState.HasUrl) { "No URL available to retrieve picture" }
            val baseUrl = urlState.url?.toString()?.removeSuffix("/").orEmpty()
            CameraImageMetadata(
                state = entity?.state,
                url = entity?.entityPicturePath()?.let { "$baseUrl$it" },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch entity or entity does not exist")
            CameraImageMetadata(error = true)
        }
    }

    private suspend fun fetchBitmap(url: String, appWidgetId: Int): Bitmap? {
        Timber.d("Fetching camera image")
        return try {
            context.imageLoader.execute(
                ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .networkCachePolicy(CachePolicy.READ_ONLY)
                    .size(bitmapSize(appWidgetManager, appWidgetId))
                    .precision(Precision.INEXACT)
                    .build(),
            ).image?.toBitmap()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Unable to fetch image")
            null
        }
    }

    private fun createRemoteViews(
        appWidgetId: Int,
        widget: CameraWidgetEntity?,
        metadata: CameraImageMetadata,
        bitmap: Bitmap?,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.widget_camera).apply {
        if (widget != null) {
            setViewVisibility(R.id.widgetCameraError, if (metadata.error) View.VISIBLE else View.GONE)
            val hasImageUrl = metadata.url != null
            setViewVisibility(R.id.widgetCameraImage, if (hasImageUrl) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.widgetCameraPlaceholder, if (hasImageUrl) View.GONE else View.VISIBLE)
            bitmap?.let { setImageViewBitmap(R.id.widgetCameraImage, it) }
            val pendingIntent = createTapPendingIntent(widget, appWidgetId)
            setOnClickPendingIntent(R.id.widgetCameraImage, pendingIntent)
            setOnClickPendingIntent(R.id.widgetCameraPlaceholder, pendingIntent)
        }
    }

    private fun createTapPendingIntent(widget: CameraWidgetEntity, appWidgetId: Int): PendingIntent =
        when (widget.tapAction) {
            WidgetTapAction.OPEN -> PendingIntent.getActivity(
                context,
                appWidgetId,
                context.intentLaunchWithNavigateTo(
                    FrontendTarget.EntityMoreInfo(widget.entityId),
                    widget.serverId,
                ),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            else -> PendingIntent.getBroadcast(
                context,
                appWidgetId,
                Intent(context, CameraWidget::class.java).apply {
                    action = CameraWidget.UPDATE_IMAGE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
}

private data class CameraImageMetadata(
    val state: String? = null,
    val url: String? = null,
    val error: Boolean = false,
) {
    val hasUsableState: Boolean get() = state != null && state !in STATES_WITHOUT_IMAGE
}

private class WidgetUpdateLock {
    val mutex = Mutex()
    var users = 0
}

// States of an image entity that carry no image, for example while Home Assistant restarts
private val STATES_WITHOUT_IMAGE = setOf(STATE_UNAVAILABLE, STATE_UNKNOWN)

private fun CameraWidgetEntity.identity() = DrawnEntityIdentity(serverId, entityId)

private fun CompressedStateChangedEvent.toImageUpdateReason(entityId: String): ImageUpdateReason? {
    val addedState = added?.get(entityId)?.state?.jsonPrimitive?.contentOrNull
    val changed = changed?.get(entityId)
    val changedState = changed?.plus?.state?.jsonPrimitive?.contentOrNull
    return when {
        entityId in removed.orEmpty() -> null
        added?.containsKey(entityId) == true && addedState !in STATES_WITHOUT_IMAGE ->
            ImageUpdateReason.CheckCurrentState
        changedState != null && changedState !in STATES_WITHOUT_IMAGE -> ImageUpdateReason.CheckCurrentState
        changed != null && (changed.plus != null || changed.minus != null) && changedState == null ->
            ImageUpdateReason.RetryOnlyIfUncached
        else -> null
    }
}

/** The watch of the image entity [entityId] on server [serverId], see [CameraWidget.watchImageEntities]. */
private data class ImageWatch(val serverId: Int, val entityId: String, val job: Job) {
    /** Whether this watch is for the entity [widget] shows. */
    fun isFor(widget: CameraWidgetEntity?): Boolean = widget?.serverId == serverId && widget.entityId == entityId
}
