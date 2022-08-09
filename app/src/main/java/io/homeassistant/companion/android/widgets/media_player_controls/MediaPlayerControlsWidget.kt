package io.homeassistant.companion.android.widgets.media_player_controls

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.content.getSystemService
import com.google.android.material.color.DynamicColors
import com.mikepenz.iconics.IconicsDrawable
import com.squareup.picasso.Picasso
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.url.UrlRepository
import io.homeassistant.companion.android.database.widget.MediaPlayerControlsWidgetDao
import io.homeassistant.companion.android.database.widget.MediaPlayerControlsWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.widgets.BaseWidgetProvider
import kotlinx.coroutines.launch
import java.util.LinkedList
import javax.inject.Inject
import kotlin.collections.HashMap
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class MediaPlayerControlsWidget : BaseWidgetProvider() {

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
        internal const val CALL_VOLUME_DOWN =
            "io.homeassistant.companion.android.widgets.media_player_controls.MediaPlayerControlsWidget.CALL_VOLUME_DOWN"
        internal const val CALL_VOLUME_UP =
            "io.homeassistant.companion.android.widgets.media_player_controls.MediaPlayerControlsWidget.CALL_VOLUME_UP"

        internal const val EXTRA_ENTITY_ID = "EXTRA_ENTITY_ID"
        internal const val EXTRA_LABEL = "EXTRA_LABEL"
        internal const val EXTRA_SHOW_VOLUME = "EXTRA_SHOW_VOLUME"
        internal const val EXTRA_SHOW_SOURCE = "EXTRA_SHOW_VOLUME_SOURCE"
        internal const val EXTRA_SHOW_SKIP = "EXTRA_INCLUDE_SKIP"
        internal const val EXTRA_SHOW_SEEK = "EXTRA_INCLUDE_SEEK"
        internal const val EXTRA_BACKGROUND_TYPE = "EXTRA_BACKGROUND_TYPE"

        private var isSubscribed = false
    }

    @Inject
    lateinit var urlUseCase: UrlRepository

    @Inject
    lateinit var mediaPlayCtrlWidgetDao: MediaPlayerControlsWidgetDao

    override fun isSubscribed(): Boolean = isSubscribed

    override fun setSubscribed(subscribed: Boolean) {
        isSubscribed = subscribed
    }

    override fun getWidgetProvider(context: Context): ComponentName =
        ComponentName(context, MediaPlayerControlsWidget::class.java)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // There may be multiple widgets active, so update all of them
        appWidgetIds.forEach { appWidgetId ->
            updateView(
                context,
                appWidgetId,
                appWidgetManager
            )
        }
    }

    private fun updateView(
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
            onScreenOn(context)
        }
    }

    override suspend fun getWidgetRemoteViews(context: Context, appWidgetId: Int, suggestedEntity: Entity<Map<String, Any>>?): RemoteViews {
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

        val volumeDownIntent = Intent(context, MediaPlayerControlsWidget::class.java).apply {
            action = CALL_VOLUME_DOWN
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }

        val volumeUpIntent = Intent(context, MediaPlayerControlsWidget::class.java).apply {
            action = CALL_VOLUME_UP
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }

        val widget = mediaPlayCtrlWidgetDao.get(appWidgetId)
        val useDynamicColors = widget?.backgroundType == WidgetBackgroundType.DYNAMICCOLOR && DynamicColors.isDynamicColorAvailable()
        return RemoteViews(context.packageName, if (useDynamicColors) R.layout.widget_media_controls_wrapper_dynamiccolor else R.layout.widget_media_controls_wrapper_default).apply {
            if (widget != null) {
                val entityIds: LinkedList<String> = LinkedList()
                entityIds.addAll(widget.entityId.split(","))

                var label: String? = widget.label
                val showVolume: Boolean = widget.showVolume
                val showSkip: Boolean = widget.showSkip
                val showSeek: Boolean = widget.showSeek
                val showSource: Boolean = widget.showSource
                val entity = getEntity(context, entityIds, suggestedEntity)

                if (entity?.state.equals("playing")) {
                    setImageViewResource(
                        R.id.widgetPlayPauseButton,
                        R.drawable.ic_pause
                    )
                } else {
                    setImageViewResource(
                        R.id.widgetPlayPauseButton,
                        R.drawable.ic_play
                    )
                }

                val artist = (entity?.attributes?.get("media_artist") ?: entity?.attributes?.get("media_album_artist"))?.toString()
                val title = entity?.attributes?.get("media_title")?.toString()
                val album = entity?.attributes?.get("media_album_name")?.toString()
                var icon = entity?.attributes?.get("icon")?.toString()

                if ((artist != null || album != null) && title != null) {
                    setTextViewText(
                        R.id.widgetMediaInfoArtist,
                        when {
                            artist != null && album != null -> "$artist - $album"
                            album != null -> album
                            else -> artist
                        }
                    )
                    setTextViewText(
                        R.id.widgetMediaInfoTitle,
                        title
                    )
                    setViewVisibility(
                        R.id.widgetMediaInfoTitle,
                        View.VISIBLE
                    )
                    setViewVisibility(
                        R.id.widgetMediaInfoArtist,
                        View.VISIBLE
                    )
                    setViewVisibility(
                        R.id.widgetLabel,
                        View.GONE
                    )
                } else {
                    if (artist != null) {
                        label = artist
                    }
                    if (artist == null && title != null) {
                        label = title
                    }
                    if (artist == null && title == null && album != null) {
                        label = album
                    }
                    setTextViewText(
                        R.id.widgetLabel,
                        label ?: entity?.entityId
                    )
                    setViewVisibility(
                        R.id.widgetMediaInfoTitle,
                        View.GONE
                    )
                    setViewVisibility(
                        R.id.widgetMediaInfoArtist,
                        View.GONE
                    )
                    setViewVisibility(
                        R.id.widgetLabel,
                        View.VISIBLE
                    )
                }

                if (icon == null || !icon.startsWith("mdi") || !icon.contains(":")) {
                    icon = "mdi:cast"
                }

                val iconName = icon.split(":")[1]
                val iconDrawable: Bitmap = IconicsDrawable(context, "cmd-$iconName").toBitmap()
                setImageViewBitmap(
                    R.id.widgetSourceIcon,
                    iconDrawable
                )

                val entityPictureUrl = entity?.attributes?.get("entity_picture")?.toString()
                val baseUrl = urlUseCase.getUrl().toString().removeSuffix("/")
                val url = if (entityPictureUrl?.startsWith("http") == true) entityPictureUrl else "$baseUrl$entityPictureUrl"
                if (entityPictureUrl == null) {
                    setImageViewResource(
                        R.id.widgetMediaImage,
                        R.drawable.app_icon_round
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
                        if (BuildConfig.DEBUG) {
                            Picasso.get().isLoggingEnabled = true
                            Picasso.get().setIndicatorsEnabled(true)
                        }
                        try {
                            Picasso.get().load(url).resize(1024, 1024).into(
                                this,
                                R.id.widgetMediaImage,
                                intArrayOf(appWidgetId)
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Unable to load image", e)
                        }
                        Log.d(TAG, "Fetch and load complete")
                    }
                }

                setOnClickPendingIntent(
                    R.id.widgetMediaImage,
                    PendingIntent.getBroadcast(
                        context,
                        appWidgetId,
                        updateMediaIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                setOnClickPendingIntent(
                    R.id.widgetMediaPlaceholder,
                    PendingIntent.getBroadcast(
                        context,
                        appWidgetId,
                        updateMediaIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                setOnClickPendingIntent(
                    R.id.widgetPlayPauseButton,
                    PendingIntent.getBroadcast(
                        context,
                        appWidgetId,
                        playPauseIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )

                if (showVolume) {
                    setOnClickPendingIntent(
                        R.id.widgetVolumeDownButton,
                        PendingIntent.getBroadcast(
                            context,
                            appWidgetId,
                            volumeDownIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                    setOnClickPendingIntent(
                        R.id.widgetVolumeUpButton,
                        PendingIntent.getBroadcast(
                            context,
                            appWidgetId,
                            volumeUpIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                    setViewVisibility(R.id.widgetVolumeDownButton, View.VISIBLE)
                    setViewVisibility(R.id.widgetVolumeUpButton, View.VISIBLE)
                } else {
                    setViewVisibility(R.id.widgetVolumeDownButton, View.GONE)
                    setViewVisibility(R.id.widgetVolumeUpButton, View.GONE)
                }

                if (showSkip) {
                    setOnClickPendingIntent(
                        R.id.widgetPrevTrackButton,
                        PendingIntent.getBroadcast(
                            context,
                            appWidgetId,
                            prevTrackIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                    setOnClickPendingIntent(
                        R.id.widgetNextTrackButton,
                        PendingIntent.getBroadcast(
                            context,
                            appWidgetId,
                            nextTrackIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
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
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                    setOnClickPendingIntent(
                        R.id.widgetFastForwardButton,
                        PendingIntent.getBroadcast(
                            context,
                            appWidgetId,
                            fastForwardIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                    setViewVisibility(R.id.widgetRewindButton, View.VISIBLE)
                    setViewVisibility(R.id.widgetFastForwardButton, View.VISIBLE)
                } else {
                    setViewVisibility(R.id.widgetRewindButton, View.GONE)
                    setViewVisibility(R.id.widgetFastForwardButton, View.GONE)
                }

                if (showSource) {
                    setTextViewText(R.id.widgetSourceLabel, entity?.attributes?.get("friendly_name").toString())
                    setViewVisibility(R.id.widgetSourceLabel, View.VISIBLE)
                } else {
                    setViewVisibility(R.id.widgetSourceLabel, View.INVISIBLE)
                }
            } else {
                setTextViewText(R.id.widgetLabel, "")
            }
        }
    }

    override suspend fun getAllWidgetIds(context: Context): List<Int> {
        return mediaPlayCtrlWidgetDao.getAll().map { it.id }
    }

    private suspend fun getEntity(context: Context, entityIds: List<String>, suggestedEntity: Entity<Map<String, Any>>?): Entity<Map<String, Any>>? {
        val entity: Entity<Map<String, Any>>?
        try {
            entity = if (suggestedEntity != null && entityIds.contains(suggestedEntity.entityId)) {
                suggestedEntity
            } else {
                val entities: LinkedList<Entity<Map<String, Any>>?> = LinkedList()
                entityIds.forEach {
                    val e = integrationUseCase.getEntity(it)
                    if (e?.state == "playing") return e
                    entities.add(e)
                }
                return entities[0]
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to fetch entity or entity does not exist")
            if (lastIntent == UPDATE_MEDIA_IMAGE)
                Toast.makeText(context, commonR.string.widget_entity_fetch_error, Toast.LENGTH_LONG).show()
            return null
        }

        return entity
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
            UPDATE_VIEW -> updateView(
                context,
                appWidgetId
            )
            RECEIVE_DATA -> {
                saveEntityConfiguration(context, intent.extras, appWidgetId)
                super.onScreenOn(context)
            }
            UPDATE_MEDIA_IMAGE -> updateView(context, appWidgetId)
            CALL_PREV_TRACK -> callPreviousTrackService(context, appWidgetId)
            CALL_REWIND -> callRewindService(context, appWidgetId)
            CALL_PLAYPAUSE -> callPlayPauseService(context, appWidgetId)
            CALL_FASTFORWARD -> callFastForwardService(context, appWidgetId)
            CALL_NEXT_TRACK -> callNextTrackService(context, appWidgetId)
            CALL_VOLUME_DOWN -> callVolumeDownService(context, appWidgetId)
            CALL_VOLUME_UP -> callVolumeUpService(context, appWidgetId)
        }
    }

    override fun saveEntityConfiguration(context: Context, extras: Bundle?, appWidgetId: Int) {
        if (extras == null) return

        val entitySelection: String? = extras.getString(EXTRA_ENTITY_ID)
        val labelSelection: String? = extras.getString(EXTRA_LABEL)
        val showSkip: Boolean? = extras.getBoolean(EXTRA_SHOW_SKIP)
        val showSeek: Boolean? = extras.getBoolean(EXTRA_SHOW_SEEK)
        val showVolume: Boolean? = extras.getBoolean(EXTRA_SHOW_VOLUME)
        val showSource: Boolean? = extras.getBoolean(EXTRA_SHOW_SOURCE)
        val backgroundType: WidgetBackgroundType = extras.getSerializable(EXTRA_BACKGROUND_TYPE) as WidgetBackgroundType

        if (entitySelection == null || showSkip == null || showSeek == null || showVolume == null || showSource == null) {
            Log.e(TAG, "Did not receive complete configuration data")
            return
        }

        mainScope.launch {
            Log.d(
                TAG,
                "Saving service call config data:" + System.lineSeparator() +
                    "entity id: " + entitySelection + System.lineSeparator()
            )
            mediaPlayCtrlWidgetDao.add(
                MediaPlayerControlsWidgetEntity(
                    appWidgetId,
                    entitySelection,
                    labelSelection,
                    showSkip,
                    showSeek,
                    showVolume,
                    showSource,
                    backgroundType
                )
            )

            onUpdate(context, AppWidgetManager.getInstance(context), intArrayOf(appWidgetId))
        }
    }

    override suspend fun onEntityStateChanged(context: Context, entity: Entity<*>) {
        mediaPlayCtrlWidgetDao.getAll().forEach {
            val entityIds = it.entityId.split(",")
            if (entityIds.contains(entity.entityId)) {
                mainScope.launch {
                    val views = getWidgetRemoteViews(context, it.id, getEntity(context, entityIds, null))
                    AppWidgetManager.getInstance(context).updateAppWidget(it.id, views)
                }
            }
        }
    }

    private fun callPreviousTrackService(context: Context, appWidgetId: Int) {
        mainScope.launch {
            Log.d(TAG, "Retrieving media player entity for app widget $appWidgetId")
            val entity: MediaPlayerControlsWidgetEntity? = mediaPlayCtrlWidgetDao.get(appWidgetId)

            if (entity == null) {
                Log.d(TAG, "Failed to retrieve media player entity")
                return@launch
            }

            Log.d(
                TAG,
                "Calling previous track service:" + System.lineSeparator() +
                    "entity id: " + entity.entityId + System.lineSeparator()
            )

            val domain = "media_player"
            val service = "media_previous_track"
            val entityId: String = getEntity(context, entity.entityId.split(","), null)?.entityId.toString()

            val serviceDataMap: HashMap<String, Any> = hashMapOf("entity_id" to entityId)

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
                TAG,
                "Calling rewind service:" + System.lineSeparator() +
                    "entity id: " + entity.entityId + System.lineSeparator()
            )

            val currentEntityInfo = integrationUseCase.getEntity(entity.entityId)
            if (currentEntityInfo == null) {
                Log.d(TAG, "Failed to fetch entity or entity does not exist")
                if (lastIntent != Intent.ACTION_SCREEN_ON)
                    Toast.makeText(context, commonR.string.widget_entity_fetch_error, Toast.LENGTH_LONG).show()
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
            val entityId: String = getEntity(context, entity.entityId.split(","), null)?.entityId.toString()

            val serviceDataMap: HashMap<String, Any> = hashMapOf(
                "entity_id" to entityId,
                "seek_position" to currentTime - 10
            )

            integrationUseCase.callService(domain, service, serviceDataMap)
        }
    }

    private fun callPlayPauseService(context: Context, appWidgetId: Int) {
        mainScope.launch {
            Log.d(TAG, "Retrieving media player entity for app widget $appWidgetId")
            val entity: MediaPlayerControlsWidgetEntity? = mediaPlayCtrlWidgetDao.get(appWidgetId)

            if (entity == null) {
                Log.d(TAG, "Failed to retrieve media player entity")
                return@launch
            }

            Log.d(
                TAG,
                "Calling play/pause service:" + System.lineSeparator() +
                    "entity id: " + entity.entityId + System.lineSeparator()
            )

            val domain = "media_player"
            val service = "media_play_pause"
            val entityId: String = getEntity(context, entity.entityId.split(","), null)?.entityId.toString()

            val serviceDataMap: HashMap<String, Any> = hashMapOf("entity_id" to entityId)

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
                TAG,
                "Calling fast forward service:" + System.lineSeparator() +
                    "entity id: " + entity.entityId + System.lineSeparator()
            )

            val currentEntityInfo = integrationUseCase.getEntity(entity.entityId)
            if (currentEntityInfo == null) {
                Log.d(TAG, "Failed to fetch entity or entity does not exist")
                if (lastIntent != Intent.ACTION_SCREEN_ON)
                    Toast.makeText(context, commonR.string.widget_entity_fetch_error, Toast.LENGTH_LONG).show()
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
            val entityId: String = getEntity(context, entity.entityId.split(","), null)?.entityId.toString()

            val serviceDataMap: HashMap<String, Any> = hashMapOf(
                "entity_id" to entityId,
                "seek_position" to currentTime + 10
            )

            integrationUseCase.callService(domain, service, serviceDataMap)
        }
    }

    private fun callNextTrackService(context: Context, appWidgetId: Int) {
        mainScope.launch {
            Log.d(TAG, "Retrieving media player entity for app widget $appWidgetId")
            val entity: MediaPlayerControlsWidgetEntity? = mediaPlayCtrlWidgetDao.get(appWidgetId)

            if (entity == null) {
                Log.d(TAG, "Failed to retrieve media player entity")
                return@launch
            }

            Log.d(
                TAG,
                "Calling next track service:" + System.lineSeparator() +
                    "entity id: " + entity.entityId + System.lineSeparator()
            )

            val domain = "media_player"
            val service = "media_next_track"
            val entityId: String = getEntity(context, entity.entityId.split(","), null)?.entityId.toString()

            val serviceDataMap: HashMap<String, Any> = hashMapOf("entity_id" to entityId)

            integrationUseCase.callService(domain, service, serviceDataMap)
        }
    }

    private fun callVolumeDownService(context: Context, appWidgetId: Int) {
        mainScope.launch {
            Log.d(TAG, "Retrieving media player entity for app widget $appWidgetId")
            val entity: MediaPlayerControlsWidgetEntity? = mediaPlayCtrlWidgetDao.get(appWidgetId)

            if (entity == null) {
                Log.d(TAG, "Failed to retrieve media player entity")
                return@launch
            }

            Log.d(
                TAG,
                "Calling volume down service:" + System.lineSeparator() +
                    "entity id: " + entity.entityId + System.lineSeparator()
            )

            val domain = "media_player"
            val service = "volume_down"
            val entityId: String = getEntity(context, entity.entityId.split(","), null)?.entityId.toString()

            val serviceDataMap: HashMap<String, Any> = hashMapOf("entity_id" to entityId)

            integrationUseCase.callService(domain, service, serviceDataMap)
        }
    }

    private fun callVolumeUpService(context: Context, appWidgetId: Int) {
        mainScope.launch {
            Log.d(TAG, "Retrieving media player entity for app widget $appWidgetId")
            val entity: MediaPlayerControlsWidgetEntity? = mediaPlayCtrlWidgetDao.get(appWidgetId)

            if (entity == null) {
                Log.d(TAG, "Failed to retrieve media player entity")
                return@launch
            }

            Log.d(
                TAG,
                "Calling volume up service:" + System.lineSeparator() +
                    "entity id: " + entity.entityId + System.lineSeparator()
            )

            val domain = "media_player"
            val service = "volume_up"
            val entityId: String = getEntity(context, entity.entityId.split(","), null)?.entityId.toString()

            val serviceDataMap: HashMap<String, Any> = hashMapOf("entity_id" to entityId)

            integrationUseCase.callService(domain, service, serviceDataMap)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        // When the user deletes the widget, delete the preference associated with it.
        mainScope.launch {
            mediaPlayCtrlWidgetDao.deleteAll(appWidgetIds)
        }
    }

    override fun onEnabled(context: Context) {
        // Enter relevant functionality for when the first widget is created
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
    }

    private fun isConnectionActive(context: Context): Boolean {
        val connectivityManager = context.getSystemService<ConnectivityManager>()
        val activeNetworkInfo = connectivityManager?.activeNetworkInfo
        return activeNetworkInfo?.isConnected ?: false
    }
}
