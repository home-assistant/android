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
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.squareup.picasso.Picasso
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.widget.CameraWidgetDao
import io.homeassistant.companion.android.database.widget.CameraWidgetEntity
import io.homeassistant.companion.android.util.hasActiveConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CameraWidget : AppWidgetProvider() {

    companion object {
        private const val TAG = "CameraWidget"
        internal const val RECEIVE_DATA =
            "io.homeassistant.companion.android.widgets.camera.CameraWidget.RECEIVE_DATA"
        internal const val UPDATE_IMAGE =
            "io.homeassistant.companion.android.widgets.camera.CameraWidget.UPDATE_IMAGE"

        internal const val EXTRA_SERVER_ID = "EXTRA_SERVER_ID"
        internal const val EXTRA_ENTITY_ID = "EXTRA_ENTITY_ID"
        private var lastIntent = ""
    }

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var cameraWidgetDao: CameraWidgetDao

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // There may be multiple widgets active, so update all of them
        appWidgetIds.forEach { appWidgetId ->
            updateAppWidget(
                context,
                appWidgetId,
                appWidgetManager
            )
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetId: Int,
        appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)
    ) {
        if (!context.hasActiveConnection()) {
            Log.d(TAG, "Skipping widget update since network connection is not active")
            return
        }
        mainScope.launch {
            val views = getWidgetRemoteViews(context, appWidgetId)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun updateAllWidgets(context: Context) {
        mainScope.launch {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val systemWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, CameraWidget::class.java))
            val dbWidgetList = cameraWidgetDao.getAll()

            val invalidWidgetIds = dbWidgetList
                .filter { !systemWidgetIds.contains(it.id) }
                .map { it.id }
            if (invalidWidgetIds.isNotEmpty()) {
                Log.i(TAG, "Found widgets $invalidWidgetIds in database, but not in AppWidgetManager - sending onDeleted")
                onDeleted(context, invalidWidgetIds.toIntArray())
            }

            val cameraWidgetList = dbWidgetList.filter { systemWidgetIds.contains(it.id) }
            if (cameraWidgetList.isNotEmpty()) {
                Log.d(TAG, "Updating all widgets")
                for (item in cameraWidgetList) {
                    updateAppWidget(context, item.id, appWidgetManager)
                }
            }
        }
    }

    private suspend fun getWidgetRemoteViews(context: Context, appWidgetId: Int): RemoteViews {
        val updateCameraIntent = Intent(context, CameraWidget::class.java).apply {
            action = UPDATE_IMAGE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }

        return RemoteViews(context.packageName, R.layout.widget_camera).apply {
            val widget = cameraWidgetDao.get(appWidgetId)
            if (widget != null) {
                var entityPictureUrl: String?
                try {
                    entityPictureUrl = retrieveCameraImageUrl(widget.serverId, widget.entityId)
                    setViewVisibility(R.id.widgetCameraError, View.GONE)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to fetch entity or entity does not exist", e)
                    setViewVisibility(R.id.widgetCameraError, View.VISIBLE)
                    entityPictureUrl = null
                }
                val baseUrl = serverManager.getServer(widget.serverId)?.connection?.getUrl().toString().removeSuffix("/")
                val url = "$baseUrl$entityPictureUrl"
                if (entityPictureUrl == null) {
                    setImageViewResource(
                        R.id.widgetCameraImage,
                        R.drawable.app_icon_round
                    )
                    setViewVisibility(
                        R.id.widgetCameraPlaceholder,
                        View.VISIBLE
                    )
                    setViewVisibility(
                        R.id.widgetCameraImage,
                        View.GONE
                    )
                } else {
                    setViewVisibility(
                        R.id.widgetCameraImage,
                        View.VISIBLE
                    )
                    setViewVisibility(
                        R.id.widgetCameraPlaceholder,
                        View.GONE
                    )
                    Log.d(TAG, "Fetching camera image")
                    Handler(Looper.getMainLooper()).post {
                        val picasso = Picasso.get()
                        if (BuildConfig.DEBUG) {
                            picasso.isLoggingEnabled = true
                        }
                        try {
                            picasso.invalidate(url)
                            picasso.load(url).resize(getScreenWidth(), 0).onlyScaleDown().into(
                                this,
                                R.id.widgetCameraImage,
                                intArrayOf(appWidgetId)
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Unable to fetch image", e)
                        }
                        Log.d(TAG, "Fetch and load complete")
                    }
                }

                setOnClickPendingIntent(
                    R.id.widgetCameraImage,
                    PendingIntent.getBroadcast(
                        context,
                        appWidgetId,
                        updateCameraIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                setOnClickPendingIntent(
                    R.id.widgetCameraPlaceholder,
                    PendingIntent.getBroadcast(
                        context,
                        appWidgetId,
                        updateCameraIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }
        }
    }

    private suspend fun retrieveCameraImageUrl(serverId: Int, entityId: String): String? {
        val entity = serverManager.integrationRepository(serverId).getEntity(entityId)
        return entity?.attributes?.get("entity_picture")?.toString()
    }

    override fun onReceive(context: Context, intent: Intent) {
        lastIntent = intent.action.toString()
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)

        Log.d(
            TAG,
            "Broadcast received: " + System.lineSeparator() +
                "Broadcast action: " + lastIntent + System.lineSeparator() +
                "AppWidgetId: " + appWidgetId
        )

        super.onReceive(context, intent)
        when (lastIntent) {
            RECEIVE_DATA -> saveEntityConfiguration(context, intent.extras, appWidgetId)
            UPDATE_IMAGE -> updateAppWidget(context, appWidgetId)
            Intent.ACTION_SCREEN_ON -> updateAllWidgets(context)
        }
    }

    private fun saveEntityConfiguration(context: Context, extras: Bundle?, appWidgetId: Int) {
        if (extras == null) return

        val serverSelection = if (extras.containsKey(EXTRA_SERVER_ID)) extras.getInt(EXTRA_SERVER_ID) else null
        val entitySelection: String? = extras.getString(EXTRA_ENTITY_ID)

        if (serverSelection == null || entitySelection == null) {
            Log.e(TAG, "Did not receive complete configuration data")
            return
        }

        mainScope.launch {
            Log.d(
                TAG,
                "Saving camera config data:" + System.lineSeparator() +
                    "entity id: " + entitySelection + System.lineSeparator()
            )
            cameraWidgetDao.add(
                CameraWidgetEntity(
                    appWidgetId,
                    serverSelection,
                    entitySelection
                )
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
