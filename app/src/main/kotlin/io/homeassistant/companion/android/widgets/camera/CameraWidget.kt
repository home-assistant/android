package io.homeassistant.companion.android.widgets.camera

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.RemoteViews
import androidx.core.os.BundleCompat
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Dimension
import coil3.size.Precision
import coil3.size.Size
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.database.widget.CameraWidgetDao
import io.homeassistant.companion.android.database.widget.CameraWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetTapAction
import io.homeassistant.companion.android.util.hasActiveConnection
import io.homeassistant.companion.android.webview.WebViewActivity
import io.homeassistant.companion.android.widgets.ACTION_APPWIDGET_CREATED
import io.homeassistant.companion.android.widgets.BaseWidgetProvider.Companion.UPDATE_WIDGETS
import io.homeassistant.companion.android.widgets.BaseWidgetProvider.Companion.widgetScope
import io.homeassistant.companion.android.widgets.EXTRA_WIDGET_ENTITY
import io.homeassistant.companion.android.widgets.common.RemoteViewsTarget
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import timber.log.Timber

@AndroidEntryPoint
class CameraWidget : AppWidgetProvider() {

    companion object {
        internal const val UPDATE_IMAGE =
            "io.homeassistant.companion.android.widgets.camera.CameraWidget.UPDATE_IMAGE"

        internal const val EXTRA_SERVER_ID = "EXTRA_SERVER_ID"
        internal const val EXTRA_ENTITY_ID = "EXTRA_ENTITY_ID"
        internal const val EXTRA_TAP_ACTION = "EXTRA_TAP_ACTION"
        private var lastIntent = ""
    }

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var cameraWidgetDao: CameraWidgetDao

    @Inject
    lateinit var okHttpClient: OkHttpClient

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // There may be multiple widgets active, so update all of them
        appWidgetIds.forEach { appWidgetId ->
            updateAppWidget(
                context,
                appWidgetId,
                appWidgetManager,
            )
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetId: Int,
        appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context),
    ) {
        if (!context.hasActiveConnection()) {
            Timber.d("Skipping widget update since network connection is not active")
            return
        }
        mainScope.launch {
            val views = getWidgetRemoteViews(context, appWidgetId)
            views?.let { appWidgetManager.updateAppWidget(appWidgetId, it) }
        }
    }

    private fun updateAllWidgets(context: Context) {
        mainScope.launch {
            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return@launch
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
    }

    private suspend fun getWidgetRemoteViews(context: Context, appWidgetId: Int): RemoteViews? {
        val updateCameraIntent = Intent(context, CameraWidget::class.java).apply {
            action = UPDATE_IMAGE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }

        val widget = cameraWidgetDao.get(appWidgetId)
        var widgetCameraError = false
        var url: String? = null
        if (widget != null) {
            try {
                val entityPictureUrl = retrieveCameraImageUrl(widget.serverId, widget.entityId)
                val baseUrl = serverManager.getServer(
                    widget.serverId,
                )?.connection?.getUrl().toString().removeSuffix("/")
                url = "$baseUrl$entityPictureUrl"
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch entity or entity does not exist")
                widgetCameraError = true
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
                    Handler(Looper.getMainLooper()).post {
                        try {
                            val request = ImageRequest.Builder(context)
                                .data(url)
                                .target(RemoteViewsTarget(context, appWidgetId, this, R.id.widgetCameraImage))
                                .diskCachePolicy(CachePolicy.DISABLED)
                                .memoryCachePolicy(CachePolicy.DISABLED)
                                .networkCachePolicy(CachePolicy.READ_ONLY)
                                .size(Size(getScreenWidth(), Dimension.Undefined))
                                .precision(Precision.INEXACT)
                                .build()
                            context.imageLoader.enqueue(request)
                        } catch (e: Exception) {
                            Timber.e(e, "Unable to fetch image")
                        }
                    }
                }

                val tapWidgetPendingIntent = when (widget.tapAction) {
                    WidgetTapAction.OPEN -> PendingIntent.getActivity(
                        context,
                        appWidgetId,
                        WebViewActivity.newInstance(context, "entityId:${widget.entityId}", widget.serverId),
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
        // If there is an url, Coil will call appWidgetManager.updateAppWidget
        return if (url == null) views else null
    }

    private suspend fun retrieveCameraImageUrl(serverId: Int, entityId: String): String? {
        val entity = serverManager.integrationRepository(serverId).getEntity(entityId)
        return entity?.attributes?.get("entity_picture")?.toString()
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
            UPDATE_WIDGETS -> updateAllWidgets(context)
            UPDATE_IMAGE -> updateAppWidget(context, appWidgetId)
            Intent.ACTION_SCREEN_ON -> updateAllWidgets(context)
            ACTION_APPWIDGET_CREATED -> {
                if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                    FailFast.fail { "Missing appWidgetId in intent to add widget in DAO" }
                } else {
                    widgetScope?.launch {
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
                }
                updateAllWidgets(context)
            }
        }
    }

    private fun saveEntityConfiguration(context: Context, extras: Bundle?, appWidgetId: Int) {
        if (extras == null) return

        val serverSelection = if (extras.containsKey(EXTRA_SERVER_ID)) extras.getInt(EXTRA_SERVER_ID) else null
        val entitySelection: String? = extras.getString(EXTRA_ENTITY_ID)
        val tapActionSelection = BundleCompat.getSerializable(extras, EXTRA_TAP_ACTION, WidgetTapAction::class.java)
            ?: WidgetTapAction.REFRESH

        if (serverSelection == null || entitySelection == null) {
            Timber.e("Did not receive complete configuration data")
            return
        }

        mainScope.launch {
            Timber.d(
                "Saving camera config data:" + System.lineSeparator() +
                    "entity id: " + entitySelection + System.lineSeparator(),
            )
            cameraWidgetDao.add(
                CameraWidgetEntity(
                    appWidgetId,
                    serverSelection,
                    entitySelection,
                    tapActionSelection,
                ),
            )

            onUpdate(context, AppWidgetManager.getInstance(context), intArrayOf(appWidgetId))
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        // When the user deletes the widget, delete the preference associated with it.
        mainScope.launch {
            cameraWidgetDao.deleteAll(appWidgetIds)
        }
    }

    override fun onEnabled(context: Context) {
        // Enter relevant functionality for when the first widget is created
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
    }

    private fun getScreenWidth(): Int {
        return Resources.getSystem().displayMetrics.widthPixels
    }
}
