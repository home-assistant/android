package io.homeassistant.companion.android.widgets.camera

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.os.BundleCompat
import com.squareup.picasso.Picasso
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.database.widget.CameraWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetTapAction
import io.homeassistant.companion.android.repositories.CameraWidgetRepository
import io.homeassistant.companion.android.util.DisplayUtils.getScreenWidth
import io.homeassistant.companion.android.webview.WebViewActivity
import io.homeassistant.companion.android.widgets.BaseWidgetProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CameraWidget : BaseWidgetProvider<CameraWidgetRepository, Entity<*>>() {

    companion object {
        private const val TAG = "CameraWidget"

        internal const val UPDATE_IMAGE =
            "io.homeassistant.companion.android.widgets.camera.CameraWidget.UPDATE_IMAGE"

        internal const val ENTITY_PICTURE_ATTRIBUTE = "entity_picture"

        internal const val EXTRA_SERVER_ID = "EXTRA_SERVER_ID"
        internal const val EXTRA_ENTITY_ID = "EXTRA_ENTITY_ID"
        internal const val EXTRA_TAP_ACTION = "EXTRA_TAP_ACTION"
    }

    private var lastCameraBitmap: Bitmap? = null

    override fun getWidgetProvider(context: Context): ComponentName =
        ComponentName(context, CameraWidget::class.java)

    override suspend fun getWidgetRemoteViews(context: Context, appWidgetId: Int, hasActiveConnection: Boolean, suggestedEntity: Entity<*>?): RemoteViews {
        val updateCameraIntent = Intent(context, CameraWidget::class.java).apply {
            action = UPDATE_IMAGE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }

        return RemoteViews(context.packageName, R.layout.widget_camera).apply {
            val widget = repository.get(appWidgetId)
            if (widget != null) {
                val buildImageUrl = buildImageUrl(widget)

                setViewVisibility(
                    R.id.widgetCameraError,
                    if (buildImageUrl.isNullOrEmpty()) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }
                )

                updateBitmapCameraImage(context, hasActiveConnection, this, appWidgetId, buildImageUrl)

                val tapWidgetPendingIntent = when (widget.tapAction) {
                    WidgetTapAction.OPEN -> PendingIntent.getActivity(
                        context,
                        appWidgetId,
                        WebViewActivity.newInstance(context, "entityId:${widget.entityId}", widget.serverId),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    else -> PendingIntent.getBroadcast(
                        context,
                        appWidgetId,
                        updateCameraIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                }
                setOnClickPendingIntent(R.id.widgetLayout, tapWidgetPendingIntent)
            }
        }
    }

    private suspend fun buildImageUrl(cameraWidget: CameraWidgetEntity): String? {
        val baseUrl = getServerUrl(cameraWidget.serverId)
        val entityPictureUrl = retrieveCameraImageUrl(cameraWidget.serverId, cameraWidget.entityId)
        entityPictureUrl?.let {
            return "$baseUrl$entityPictureUrl"
        }
        return null
    }

    private suspend fun retrieveCameraImageUrl(serverId: Int, entityId: String): String? {
        val entity: Entity<Map<String, Any>>?
        try {
            entity = serverManager.integrationRepository(serverId).getEntity(entityId)
            return entity?.attributes?.get(ENTITY_PICTURE_ATTRIBUTE)?.toString()
        } catch (e: Exception) {
            return null
        }
    }

    private fun getServerUrl(serverId: Int): String {
        return serverManager.getServer(serverId)?.connection?.getUrl().toString().removeSuffix("/")
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        when (lastIntent) {
            UPDATE_IMAGE -> forceUpdateView(context, appWidgetId)
        }
    }

    override fun saveEntityConfiguration(context: Context, extras: Bundle?, appWidgetId: Int) {
        if (extras == null) return

        val serverSelection = if (extras.containsKey(EXTRA_SERVER_ID)) extras.getInt(EXTRA_SERVER_ID) else null
        val entitySelection: String? = extras.getString(EXTRA_ENTITY_ID)
        val tapActionSelection = BundleCompat.getSerializable(extras, EXTRA_TAP_ACTION, WidgetTapAction::class.java)
            ?: WidgetTapAction.REFRESH

        if (serverSelection == null || entitySelection == null) {
            Log.e(TAG, "Did not receive complete configuration data")
            return
        }

        widgetScope?.launch {
            Log.d(
                TAG,
                "Saving camera config data:" + System.lineSeparator() +
                    "entity id: " + entitySelection + System.lineSeparator()
            )
            repository.add(
                CameraWidgetEntity(id = appWidgetId, entityId = entitySelection, serverId = serverSelection, tapAction = tapActionSelection)
            )
        }
    }

    private fun updateBitmapCameraImage(context: Context, hasActiveConnection: Boolean, views: RemoteViews, appWidgetId: Int, url: String?) {
        if (hasActiveConnection && !url.isNullOrEmpty()) {
            widgetWorkScope?.launch {
                val picasso = Picasso.get()
                picasso.isLoggingEnabled = BuildConfig.DEBUG
                try {
                    picasso.invalidate(url)
                    lastCameraBitmap = picasso.load(url)
                        .stableKey(url)
                        .resize(getScreenWidth(), 0)
                        .onlyScaleDown().get()

                    widgetScope?.launch {
                        views.setViewVisibility(R.id.widgetCameraImage, View.VISIBLE)
                        views.setViewVisibility(R.id.widgetCameraError, View.GONE)
                        views.setViewVisibility(R.id.widgetCameraPlaceholder, View.GONE)
                        views.setImageViewBitmap(R.id.widgetCameraImage, lastCameraBitmap)
                        AppWidgetManager.getInstance(context).partiallyUpdateAppWidget(appWidgetId, views)
                        Log.d(TAG, "Fetch and load complete")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to fetch image", e)
                }
            }
        } else {
            widgetScope?.launch {
                lastCameraBitmap?.let {
                    views.setImageViewBitmap(R.id.widgetCameraImage, it)
                    AppWidgetManager.getInstance(context).partiallyUpdateAppWidget(appWidgetId, views)
                }
            }
        }
    }

    override suspend fun onEntityStateChanged(context: Context, appWidgetId: Int, entity: Entity<*>) {
        super.onEntityStateChanged(context, appWidgetId, entity)
    }

    override suspend fun getUpdates(serverId: Int, entityIds: List<String>): Flow<Entity<Map<String, Any>>> = serverManager.integrationRepository(serverId).getEntityUpdates(entityIds) as Flow<Entity<Map<String, Any>>>
}
