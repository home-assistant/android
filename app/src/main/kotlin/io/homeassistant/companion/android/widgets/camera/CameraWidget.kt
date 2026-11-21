package io.homeassistant.companion.android.widgets.camera

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
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
import io.homeassistant.companion.android.common.data.integration.EntityChange
import io.homeassistant.companion.android.common.data.integration.EntityFilter
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.IMAGE_DOMAIN
import io.homeassistant.companion.android.common.data.integration.entityPicturePath
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.UrlState
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

        // The entity state each widget was last drawn with, by widget id. For an image entity the
        // state is the time its image last changed, so a new state means a new image to fetch.
        private val drawnEntityStates = ConcurrentHashMap<Int, String>()
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
    ) {
        if (!context.hasActiveConnection()) {
            Timber.d("Skipping widget update since network connection is not active")
            return
        }
        appWidgetManager.updateAppWidget(appWidgetId, getWidgetRemoteViews(context, appWidgetId))
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
            onDeleted(context, invalidWidgetIds.toIntArray())
        }

        val cameraWidgetList = dbWidgetList.filter { systemWidgetIds.contains(it.id) }
        if (cameraWidgetList.isNotEmpty()) {
            Timber.d("Updating all widgets")
            for (item in cameraWidgetList) {
                updateAppWidget(context, item.id, appWidgetManager)
            }
        }
    }

    private suspend fun getWidgetRemoteViews(context: Context, appWidgetId: Int): RemoteViews {
        val updateCameraIntent = Intent(context, CameraWidget::class.java).apply {
            action = UPDATE_IMAGE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }

        val widget = cameraWidgetDao.get(appWidgetId)
        var widgetCameraError = false
        var url: String? = null
        var entityState: String? = null
        if (widget != null) {
            if (serverManager.getServer(widget.serverId) == null) {
                Timber.w("Widget is attached to a server that has been removed")
                widgetCameraError = true
            } else {
                try {
                    val entity = serverManager.integrationRepository(widget.serverId).getEntity(widget.entityId)
                    entityState = entity?.state
                    val entityPictureUrl = entity?.entityPicturePath()

                    val urlState = serverManager.connectionStateProvider(
                        widget.serverId,
                    ).urlFlow().first()
                    check(urlState is UrlState.HasUrl) { "No URL available to retrieve picture" }
                    val baseUrl = urlState.url?.toString()?.removeSuffix("/") ?: ""
                    url = "$baseUrl$entityPictureUrl"
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Failed to fetch entity or entity does not exist")
                    widgetCameraError = true
                }
            }
        }

        val views = RemoteViews(context.packageName, R.layout.widget_camera).apply {
            if (widget != null) {
                setViewVisibility(R.id.widgetCameraError, if (widgetCameraError) View.VISIBLE else View.GONE)
                if (url == null) {
                    setViewVisibility(
                        R.id.widgetCameraPlaceholder,
                        View.VISIBLE,
                    )
                    setViewVisibility(
                        R.id.widgetCameraImage,
                        View.GONE,
                    )
                } else {
                    setViewVisibility(
                        R.id.widgetCameraImage,
                        View.VISIBLE,
                    )
                    setViewVisibility(
                        R.id.widgetCameraPlaceholder,
                        View.GONE,
                    )
                    Timber.d("Fetching camera image")
                    try {
                        context.imageLoader.execute(
                            ImageRequest.Builder(context)
                                .data(url)
                                // RemoteViews requires software bitmaps for serialization
                                .allowHardware(false)
                                .diskCachePolicy(CachePolicy.DISABLED)
                                .memoryCachePolicy(CachePolicy.DISABLED)
                                .networkCachePolicy(CachePolicy.READ_ONLY)
                                .size(getWidgetBitmapSize(AppWidgetManager.getInstance(context), appWidgetId))
                                .precision(Precision.INEXACT)
                                .build(),
                        ).image?.toBitmap()?.let {
                            setImageViewBitmap(R.id.widgetCameraImage, it)
                            entityState?.let { state -> drawnEntityStates[appWidgetId] = state }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.e(e, "Unable to fetch image")
                    }
                }

                val tapWidgetPendingIntent = when (widget.tapAction) {
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
                        updateCameraIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                }
                setOnClickPendingIntent(R.id.widgetCameraImage, tapWidgetPendingIntent)
                setOnClickPendingIntent(R.id.widgetCameraPlaceholder, tapWidgetPendingIntent)
            }
        }
        return views
    }

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
                imageWidgets.filter { it.id !in imageWatches }.forEach { widget ->
                    val job = widgetScope.launch {
                        serverManager.integrationRepository(widget.serverId)
                            .getEntityChanges(EntityFilter(entityIds = setOf(widget.entityId)))
                            ?.filterIsInstance<EntityChange.StateChanged>()
                            ?.collect { change ->
                                // Attribute only changes, like the access token rotation, keep the same image
                                val state = change.entity.state
                                if (state !in STATES_WITHOUT_IMAGE && state != drawnEntityStates[widget.id]) {
                                    updateAppWidget(context, widget.id)
                                }
                            }
                    }
                    imageWatches[widget.id] = ImageWatch(widget.serverId, widget.entityId, job)
                }
            }
        }
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

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        // When the user deletes the widget, delete the preference associated with it.
        appWidgetIds.forEach { drawnEntityStates.remove(it) }
        widgetScope.launch {
            withContext(Dispatchers.Main) { appWidgetIds.forEach { imageWatches.remove(it)?.job?.cancel() } }
            cameraWidgetDao.deleteAll(appWidgetIds)
        }
    }

    override fun onEnabled(context: Context) {
        // Enter relevant functionality for when the first widget is created
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
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

// States of an image entity that carry no image, for example while Home Assistant restarts
private val STATES_WITHOUT_IMAGE = setOf(STATE_UNAVAILABLE, STATE_UNKNOWN)

/** The watch of the image entity [entityId] on server [serverId], see [CameraWidget.watchImageEntities]. */
private data class ImageWatch(val serverId: Int, val entityId: String, val job: Job) {
    /** Whether this watch is for the entity [widget] shows. */
    fun isFor(widget: CameraWidgetEntity?): Boolean = widget?.serverId == serverId && widget.entityId == entityId
}
