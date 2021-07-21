package io.homeassistant.companion.android.widgets.camera

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import com.squareup.picasso.Picasso
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.url.UrlRepository
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.widget.CameraWidgetDao
import io.homeassistant.companion.android.database.widget.CameraWidgetEntity
import io.homeassistant.companion.android.widgets.DaggerProviderComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

class CameraWidget : AppWidgetProvider() {

    companion object {
        private const val TAG = "CameraWidget"
        internal const val RECEIVE_DATA =
            "io.homeassistant.companion.android.widgets.camera.CameraWidget.RECEIVE_DATA"
        internal const val UPDATE_IMAGE =
            "io.homeassistant.companion.android.widgets.camera.CameraWidget.UPDATE_IMAGE"

        internal const val EXTRA_ENTITY_ID = "EXTRA_ENTITY_ID"
        private var lastIntent = ""
    }

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    @Inject
    lateinit var urlUseCase: UrlRepository

    private lateinit var cameraWidgetDao: CameraWidgetDao

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        cameraWidgetDao = AppDatabase.getInstance(context).cameraWidgetDao()
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
        if (!isConnectionActive(context)) {
            Log.d(TAG, "Skipping widget update since network connection is not active")
            return
        }
        mainScope.launch {
            val views = getWidgetRemoteViews(context, appWidgetId)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun updateAllWidgets(
        context: Context,
        cameraWidgetList: Array<CameraWidgetEntity>?
    ) {
        if (cameraWidgetList != null) {
            Log.d(TAG, "Updating all widgets")
            for (item in cameraWidgetList) {
                updateAppWidget(context, item.id)
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
                val entityId: String = widget.entityId

                val entityPictureUrl = retrieveCameraImageUrl(context, entityId)
                val baseUrl = urlUseCase.getUrl().toString().removeSuffix("/")
                val url = "$baseUrl$entityPictureUrl"
                if (entityPictureUrl == null) {
                    setImageViewResource(
                        R.id.widgetCameraImage,
                        R.drawable.app_icon
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
                        if (BuildConfig.DEBUG)
                            Picasso.get().isLoggingEnabled = true
                        try {
                            Picasso.get().load(url).resize(1024, 600).into(
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
                        PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
                setOnClickPendingIntent(
                    R.id.widgetCameraPlaceholder,
                    PendingIntent.getBroadcast(
                        context,
                        appWidgetId,
                        updateCameraIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
            }
        }
    }

    private suspend fun retrieveCameraImageUrl(context: Context, entityId: String): String? {
        val entity: Entity<Map<String, Any>>
        try {
            entity = integrationUseCase.getEntity(entityId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch entity or entity does not exist", e)
            if (lastIntent == UPDATE_IMAGE)
                Toast.makeText(context, R.string.widget_entity_fetch_error, Toast.LENGTH_LONG).show()
            return null
        }

        return entity.attributes["entity_picture"]?.toString()
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

        ensureInjected(context)

        cameraWidgetDao = AppDatabase.getInstance(context).cameraWidgetDao()
        val cameraWidgetList = cameraWidgetDao.getAll()

        super.onReceive(context, intent)
        when (lastIntent) {
            RECEIVE_DATA -> saveEntityConfiguration(context, intent.extras, appWidgetId)
            UPDATE_IMAGE -> updateAppWidget(context, appWidgetId)
            Intent.ACTION_SCREEN_ON -> updateAllWidgets(context, cameraWidgetList)
        }
    }

    private fun saveEntityConfiguration(context: Context, extras: Bundle?, appWidgetId: Int) {
        if (extras == null) return

        val entitySelection: String? = extras.getString(EXTRA_ENTITY_ID)

        if (entitySelection == null) {
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
                    entitySelection
                )
            )

            onUpdate(context, AppWidgetManager.getInstance(context), intArrayOf(appWidgetId))
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        cameraWidgetDao = AppDatabase.getInstance(context).cameraWidgetDao()
        // When the user deletes the widget, delete the preference associated with it.
        for (appWidgetId in appWidgetIds) {
            cameraWidgetDao.delete(appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        // Enter relevant functionality for when the first widget is created
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
    }

    private fun ensureInjected(context: Context) {
        if (context.applicationContext is GraphComponentAccessor) {
            DaggerProviderComponent.builder()
                .appComponent((context.applicationContext as GraphComponentAccessor).appComponent)
                .build()
                .inject(this)
        } else {
            throw Exception("Application Context passed is not of our application!")
        }
    }

    private fun isConnectionActive(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetworkInfo = connectivityManager.activeNetworkInfo
        return activeNetworkInfo?.isConnected ?: false
    }
}
