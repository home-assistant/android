package io.homeassistant.companion.android.widgets.media_player_controls

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
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.widget.MediaPlayerControlsWidgetDao
import io.homeassistant.companion.android.database.widget.MediaPlayerControlsWidgetEntity
import io.homeassistant.companion.android.widgets.DaggerProviderComponent
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MediaPlayerControlsWidget : AppWidgetProvider() {

    companion object {
        private const val TAG = "MediaPlayCtrlsWidget"
        internal const val RECEIVE_DATA =
            "io.homeassistant.companion.android.widgets.media_player_controls.MediaPlayerControlsWidget.RECEIVE_DATA"
        internal const val UPDATE_MEDIA_IMAGE =
            "io.homeassistant.companion.android.widgets.media_player_controls.MediaPlayerControlsWidget.UPDATE_MEDIA_IMAGE"
        internal const val CALL_PREV_TRACK =
            "io.homeassistant.companion.android.widgets.media_player_controls.MediaPlayerControlsWidget.CALL_PREV_TRACK"
        internal const val CALL_REWIND =
            "io.homeassistant.companion.android.widgets.media_player_controls.MediaPlayerControlsWidget.CALL_REWIND"
        internal const val CALL_PLAYPAUSE =
            "io.homeassistant.companion.android.widgets.media_player_controls.MediaPlayerControlsWidget.CALL_PLAYPAUSE"
        internal const val CALL_FASTFORWARD =
            "io.homeassistant.companion.android.widgets.media_player_controls.MediaPlayerControlsWidget.CALL_FASTFORWARD"
        internal const val CALL_NEXT_TRACK =
            "io.homeassistant.companion.android.widgets.media_player_controls.MediaPlayerControlsWidget.CALL_NEXT_TRACK"

        internal const val EXTRA_ENTITY_ID = "EXTRA_ENTITY_ID"
        internal const val EXTRA_LABEL = "EXTRA_LABEL"
        internal const val EXTRA_SHOW_SKIP = "EXTRA_INCLUDE_SKIP"
        internal const val EXTRA_SHOW_SEEK = "EXTRA_INCLUDE_SEEK"
    }

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    lateinit var mediaPlayCtrlWidgetDao: MediaPlayerControlsWidgetDao

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        mediaPlayCtrlWidgetDao = AppDatabase.getInstance(context).mediaPlayCtrlWidgetDao()
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
        mediaPlayerWidgetList: Array<MediaPlayerControlsWidgetEntity>?
    ) {
        if (mediaPlayerWidgetList != null) {
            Log.d(TAG, "Updating all widgets")
            for (item in mediaPlayerWidgetList) {
                updateAppWidget(context, item.id)
            }
        }
    }

    private suspend fun getWidgetRemoteViews(context: Context, appWidgetId: Int): RemoteViews {
        val updateMediaIntent = Intent(context, MediaPlayerControlsWidget::class.java).apply {
            action = UPDATE_MEDIA_IMAGE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }

        val prevTrackIntent = Intent(context, MediaPlayerControlsWidget::class.java).apply {
            action = CALL_PREV_TRACK
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }

        val rewindIntent = Intent(context, MediaPlayerControlsWidget::class.java).apply {
            action = CALL_REWIND
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }

        val playPauseIntent = Intent(context, MediaPlayerControlsWidget::class.java).apply {
            action = CALL_PLAYPAUSE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }

        val fastForwardIntent = Intent(context, MediaPlayerControlsWidget::class.java).apply {
            action = CALL_FASTFORWARD
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }

        val nextTrackIntent = Intent(context, MediaPlayerControlsWidget::class.java).apply {
            action = CALL_NEXT_TRACK
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }

        return RemoteViews(context.packageName, R.layout.widget_media_controls).apply {
            val widget = mediaPlayCtrlWidgetDao.get(appWidgetId)
            if (widget != null) {
                val entityId: String = widget.entityId
                val label: String? = widget.label
                val showSkip: Boolean = widget.showSkip
                val showSeek: Boolean = widget.showSeek

                setTextViewText(
                    R.id.widgetLabel,
                    label ?: entityId
                )
                val entityPictureUrl = retrieveMediaPlayerImageUrl(context, entityId)
                if (entityPictureUrl == null) {
                    setImageViewResource(
                        R.id.widgetMediaImage,
                        R.drawable.app_icon
                    )
                    setViewVisibility(
                        R.id.widgetMediaPlaceholder,
                        View.VISIBLE
                    )
                    setViewVisibility(
                        R.id.widgetMediaImage,
                        View.GONE
                    )
                } else {
                    setViewVisibility(
                        R.id.widgetMediaImage,
                        View.VISIBLE
                    )
                    setViewVisibility(
                        R.id.widgetMediaPlaceholder,
                        View.GONE
                    )
                    Log.d(TAG, "Fetching media preview image")
                    Handler(Looper.getMainLooper()).post {
                        Picasso.get().load(entityPictureUrl).into(
                            this,
                            R.id.widgetMediaImage,
                            intArrayOf(appWidgetId)
                        )
                        Log.d(TAG, "Fetch and load complete")
                    }
                }

                setOnClickPendingIntent(
                    R.id.widgetMediaImage,
                    PendingIntent.getBroadcast(
                        context,
                        appWidgetId,
                        updateMediaIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
                setOnClickPendingIntent(
                    R.id.widgetMediaPlaceholder,
                    PendingIntent.getBroadcast(
                        context,
                        appWidgetId,
                        updateMediaIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
                setOnClickPendingIntent(
                    R.id.widgetPlayPauseButton,
                    PendingIntent.getBroadcast(
                        context,
                        appWidgetId,
                        playPauseIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )

                if (showSkip) {
                    setOnClickPendingIntent(
                        R.id.widgetPrevTrackButton,
                        PendingIntent.getBroadcast(
                            context,
                            appWidgetId,
                            prevTrackIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT
                        )
                    )
                    setOnClickPendingIntent(
                        R.id.widgetNextTrackButton,
                        PendingIntent.getBroadcast(
                            context,
                            appWidgetId,
                            nextTrackIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT
                        )
                    )
                    setViewVisibility(R.id.widgetPrevTrackButton, View.VISIBLE)
                    setViewVisibility(R.id.widgetNextTrackButton, View.VISIBLE)
                } else {
                    setViewVisibility(R.id.widgetPrevTrackButton, View.GONE)
                    setViewVisibility(R.id.widgetNextTrackButton, View.GONE)
                }

                if (showSeek) {
                    setOnClickPendingIntent(
                        R.id.widgetRewindButton,
                        PendingIntent.getBroadcast(
                            context,
                            appWidgetId,
                            rewindIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT
                        )
                    )
                    setOnClickPendingIntent(
                        R.id.widgetFastForwardButton,
                        PendingIntent.getBroadcast(
                            context,
                            appWidgetId,
                            fastForwardIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT
                        )
                    )
                    setViewVisibility(R.id.widgetRewindButton, View.VISIBLE)
                    setViewVisibility(R.id.widgetFastForwardButton, View.VISIBLE)
                } else {
                    setViewVisibility(R.id.widgetRewindButton, View.GONE)
                    setViewVisibility(R.id.widgetFastForwardButton, View.GONE)
                }
            }
        }
    }

    private suspend fun retrieveMediaPlayerImageUrl(context: Context, entityId: String): String? {
        val entity: Entity<Map<String, Any>>
        try {
            entity = integrationUseCase.getEntity(entityId)
        } catch (e: Exception) {
            Log.d(TAG, "Failed to fetch entity or entity does not exist")
            Toast.makeText(context, R.string.widget_entity_fetch_error, Toast.LENGTH_LONG).show()
            return null
        }

        return entity.attributes["entity_picture"]?.toString()
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)

        Log.d(
            TAG, "Broadcast received: " + System.lineSeparator() +
                    "Broadcast action: " + action + System.lineSeparator() +
                    "AppWidgetId: " + appWidgetId
        )

        ensureInjected(context)

        mediaPlayCtrlWidgetDao = AppDatabase.getInstance(context).mediaPlayCtrlWidgetDao()
        val mediaPlayerWidgetList = mediaPlayCtrlWidgetDao.getAll()

        super.onReceive(context, intent)
        when (action) {
            RECEIVE_DATA -> saveEntityConfiguration(context, intent.extras, appWidgetId)
            UPDATE_MEDIA_IMAGE -> updateAppWidget(context, appWidgetId)
            CALL_PREV_TRACK -> callPreviousTrackService(appWidgetId)
            CALL_REWIND -> callRewindService(context, appWidgetId)
            CALL_PLAYPAUSE -> callPlayPauseService(appWidgetId)
            CALL_FASTFORWARD -> callFastForwardService(context, appWidgetId)
            CALL_NEXT_TRACK -> callNextTrackService(appWidgetId)
            Intent.ACTION_SCREEN_ON -> updateAllWidgets(context, mediaPlayerWidgetList)
        }
    }

    private fun saveEntityConfiguration(context: Context, extras: Bundle?, appWidgetId: Int) {
        if (extras == null) return

        val entitySelection: String? = extras.getString(EXTRA_ENTITY_ID)
        val labelSelection: String? = extras.getString(EXTRA_LABEL)
        val showSkip: Boolean? = extras.getBoolean(EXTRA_SHOW_SKIP)
        val showSeek: Boolean? = extras.getBoolean(EXTRA_SHOW_SEEK)

        if (entitySelection == null || showSkip == null || showSeek == null) {
            Log.e(TAG, "Did not receive complete configuration data")
            return
        }

        mainScope.launch {
            Log.d(
                TAG, "Saving service call config data:" + System.lineSeparator() +
                        "entity id: " + entitySelection + System.lineSeparator()
            )
            mediaPlayCtrlWidgetDao.add(
                MediaPlayerControlsWidgetEntity(
                    appWidgetId,
                    entitySelection,
                    labelSelection,
                    showSkip,
                    showSeek
                )
            )

            onUpdate(context, AppWidgetManager.getInstance(context), intArrayOf(appWidgetId))
        }
    }

    private fun callPreviousTrackService(appWidgetId: Int) {
        mainScope.launch {
            Log.d(TAG, "Retrieving media player entity for app widget $appWidgetId")
            val entity: MediaPlayerControlsWidgetEntity? = mediaPlayCtrlWidgetDao.get(appWidgetId)

            if (entity == null) {
                Log.d(TAG, "Failed to retrieve media player entity")
                return@launch
            }

            Log.d(
                TAG, "Calling previous track service:" + System.lineSeparator() +
                        "entity id: " + entity.entityId + System.lineSeparator()
            )

            val domain = "media_player"
            val service = "media_previous_track"

            val serviceDataMap: HashMap<String, Any> = hashMapOf("entity_id" to entity.entityId)

            integrationUseCase.callService(domain, service, serviceDataMap)
        }
    }

    private fun callRewindService(context: Context, appWidgetId: Int) {
        mainScope.launch {
            Log.d(TAG, "Retrieving media player entity for app widget $appWidgetId")
            val entity: MediaPlayerControlsWidgetEntity? = mediaPlayCtrlWidgetDao.get(appWidgetId)

            if (entity == null) {
                Log.d(TAG, "Failed to retrieve media player entity")
                return@launch
            }

            Log.d(
                TAG, "Calling rewind service:" + System.lineSeparator() +
                        "entity id: " + entity.entityId + System.lineSeparator()
            )

            val currentEntityInfo: Entity<Map<String, Any>>
            try {
                currentEntityInfo = integrationUseCase.getEntity(entity.entityId)
            } catch (e: Exception) {
                Log.d(TAG, "Failed to fetch entity or entity does not exist")
                Toast.makeText(context, R.string.widget_entity_fetch_error, Toast.LENGTH_LONG).show()
                return@launch
            }

            val fetchedAttributes = currentEntityInfo.attributes
            val currentTime = fetchedAttributes["media_position"]?.toString()?.toDoubleOrNull()

            if (currentTime == null) {
                Log.d(TAG, "Failed to get entity current time, aborting call")
                return@launch
            }

            val domain = "media_player"
            val service = "media_seek"

            val serviceDataMap: HashMap<String, Any> = hashMapOf(
                "entity_id" to entity.entityId,
                "seek_position" to currentTime - 10
            )

            integrationUseCase.callService(domain, service, serviceDataMap)
        }
    }

    private fun callPlayPauseService(appWidgetId: Int) {
        mainScope.launch {
            Log.d(TAG, "Retrieving media player entity for app widget $appWidgetId")
            val entity: MediaPlayerControlsWidgetEntity? = mediaPlayCtrlWidgetDao.get(appWidgetId)

            if (entity == null) {
                Log.d(TAG, "Failed to retrieve media player entity")
                return@launch
            }

            Log.d(
                TAG, "Calling play/pause service:" + System.lineSeparator() +
                        "entity id: " + entity.entityId + System.lineSeparator()
            )

            val domain = "media_player"
            val service = "media_play_pause"

            val serviceDataMap: HashMap<String, Any> = hashMapOf("entity_id" to entity.entityId)

            integrationUseCase.callService(domain, service, serviceDataMap)
        }
    }

    private fun callFastForwardService(context: Context, appWidgetId: Int) {
        mainScope.launch {
            Log.d(TAG, "Retrieving media player entity for app widget $appWidgetId")
            val entity: MediaPlayerControlsWidgetEntity? = mediaPlayCtrlWidgetDao.get(appWidgetId)

            if (entity == null) {
                Log.d(TAG, "Failed to retrieve media player entity")
                return@launch
            }

            Log.d(
                TAG, "Calling fast forward service:" + System.lineSeparator() +
                        "entity id: " + entity.entityId + System.lineSeparator()
            )

            val currentEntityInfo: Entity<Map<String, Any>>
            try {
                currentEntityInfo = integrationUseCase.getEntity(entity.entityId)
            } catch (e: Exception) {
                Log.d(TAG, "Failed to fetch entity or entity does not exist")
                Toast.makeText(context, R.string.widget_entity_fetch_error, Toast.LENGTH_LONG).show()
                return@launch
            }

            val fetchedAttributes = currentEntityInfo.attributes
            val currentTime = fetchedAttributes["media_position"]?.toString()?.toDoubleOrNull()

            if (currentTime == null) {
                Log.d(TAG, "Failed to get entity current time, aborting call")
                return@launch
            }

            val domain = "media_player"
            val service = "media_seek"

            val serviceDataMap: HashMap<String, Any> = hashMapOf(
                "entity_id" to entity.entityId,
                "seek_position" to currentTime + 10
            )

            integrationUseCase.callService(domain, service, serviceDataMap)
        }
    }

    private fun callNextTrackService(appWidgetId: Int) {
        mainScope.launch {
            Log.d(TAG, "Retrieving media player entity for app widget $appWidgetId")
            val entity: MediaPlayerControlsWidgetEntity? = mediaPlayCtrlWidgetDao.get(appWidgetId)

            if (entity == null) {
                Log.d(TAG, "Failed to retrieve media player entity")
                return@launch
            }

            Log.d(
                TAG, "Calling next track service:" + System.lineSeparator() +
                        "entity id: " + entity.entityId + System.lineSeparator()
            )

            val domain = "media_player"
            val service = "media_next_track"

            val serviceDataMap: HashMap<String, Any> = hashMapOf("entity_id" to entity.entityId)

            integrationUseCase.callService(domain, service, serviceDataMap)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        mediaPlayCtrlWidgetDao = AppDatabase.getInstance(context).mediaPlayCtrlWidgetDao()
        // When the user deletes the widget, delete the preference associated with it.
        for (appWidgetId in appWidgetIds) {
            mediaPlayCtrlWidgetDao.delete(appWidgetId)
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
