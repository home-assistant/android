package io.homeassistant.companion.android.widgets.mediaplayer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import coil3.imageLoader
import coil3.request.ImageRequest
import com.google.android.material.color.DynamicColors
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.MEDIA_PLAYER_DOMAIN
import io.homeassistant.companion.android.database.widget.MediaPlayerControlsWidgetDao
import io.homeassistant.companion.android.database.widget.MediaPlayerControlsWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.util.hasActiveConnection
import io.homeassistant.companion.android.widgets.BaseWidgetProvider
import io.homeassistant.companion.android.widgets.common.RemoteViewsTarget
import java.util.LinkedList
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class MediaPlayerControlsWidget : BaseWidgetProvider<MediaPlayerControlsWidgetEntity, MediaPlayerControlsWidgetDao>() {

    companion object {
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
    }

    override fun getWidgetProvider(context: Context): ComponentName =
        ComponentName(context, MediaPlayerControlsWidget::class.java)

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // There may be multiple widgets active, so update all of them
        appWidgetIds.forEach { appWidgetId ->
            updateView(
                context,
                appWidgetId,
                appWidgetManager,
            )
        }
    }

    private fun updateView(
        context: Context,
        appWidgetId: Int,
        appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context),
    ) {
        if (!context.hasActiveConnection()) {
            Timber.d("Skipping widget update since network connection is not active")
            return
        }
        widgetScope?.launch {
            val views = getWidgetRemoteViews(context, appWidgetId)
            appWidgetManager.updateAppWidget(appWidgetId, views)
            onScreenOn(context)
        }
    }

    override suspend fun getWidgetRemoteViews(
        context: Context,
        appWidgetId: Int,
        suggestedEntity: Entity?,
    ): RemoteViews {
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

        val widget = dao.get(appWidgetId)
        val useDynamicColors =
            widget?.backgroundType == WidgetBackgroundType.DYNAMICCOLOR && DynamicColors.isDynamicColorAvailable()
        return RemoteViews(
            context.packageName,
            if (useDynamicColors) {
                R.layout.widget_media_controls_wrapper_dynamiccolor
            } else {
                R.layout.widget_media_controls_wrapper_default
            },
        ).apply {
            if (widget != null) {
                val entityIds: LinkedList<String> = LinkedList()
                entityIds.addAll(widget.entityId.split(","))

                var label: String? = widget.label
                val showVolume: Boolean = widget.showVolume
                val showSkip: Boolean = widget.showSkip
                val showSeek: Boolean = widget.showSeek
                val showSource: Boolean = widget.showSource
                val entity = getEntity(context, widget.serverId, entityIds, suggestedEntity)

                if (entity?.state.equals("playing")) {
                    setImageViewResource(
                        R.id.widgetPlayPauseButton,
                        R.drawable.ic_pause,
                    )
                } else {
                    setImageViewResource(
                        R.id.widgetPlayPauseButton,
                        R.drawable.ic_play,
                    )
                }

                val artist = (
                    entity?.attributes?.get(
                        "media_artist",
                    ) ?: entity?.attributes?.get("media_album_artist")
                    )?.toString()
                val title = entity?.attributes?.get("media_title")?.toString()
                val album = entity?.attributes?.get("media_album_name")?.toString()
                val icon = entity?.attributes?.get("icon")?.toString()

                if (widget.backgroundType == WidgetBackgroundType.TRANSPARENT) {
                    setInt(R.id.widgetLayout, "setBackgroundColor", Color.TRANSPARENT)
                }

                if ((artist != null || album != null) && title != null) {
                    setTextViewText(
                        R.id.widgetMediaInfoArtist,
                        when {
                            artist != null && album != null -> "$artist - $album"
                            album != null -> album
                            else -> artist
                        },
                    )
                    setTextViewText(
                        R.id.widgetMediaInfoTitle,
                        title,
                    )
                    setViewVisibility(
                        R.id.widgetMediaInfoTitle,
                        View.VISIBLE,
                    )
                    setViewVisibility(
                        R.id.widgetMediaInfoArtist,
                        View.VISIBLE,
                    )
                    setViewVisibility(
                        R.id.widgetLabel,
                        View.GONE,
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
                        label ?: entity?.entityId,
                    )
                    setViewVisibility(
                        R.id.widgetMediaInfoTitle,
                        View.GONE,
                    )
                    setViewVisibility(
                        R.id.widgetMediaInfoArtist,
                        View.GONE,
                    )
                    setViewVisibility(
                        R.id.widgetLabel,
                        View.VISIBLE,
                    )
                }

                var iconBitmap = IconicsDrawable(context, CommunityMaterial.Icon.cmd_cast).toBitmap()
                if (icon?.startsWith("mdi") == true && icon.substringAfter(":").isNotBlank()) {
                    val iconDrawable = IconicsDrawable(context, "cmd-${icon.substringAfter(":")}")
                    if (iconDrawable.icon != null) {
                        iconBitmap = iconDrawable.toBitmap()
                    }
                }

                setImageViewBitmap(
                    R.id.widgetSourceIcon,
                    iconBitmap,
                )

                val entityPictureUrl = entity?.attributes?.get("entity_picture")?.toString()
                val baseUrl = serverManager.getServer(
                    widget.serverId,
                )?.connection?.getUrl().toString().removeSuffix("/")
                val url = if (entityPictureUrl?.startsWith("http") ==
                    true
                ) {
                    entityPictureUrl
                } else {
                    "$baseUrl$entityPictureUrl"
                }
                if (entityPictureUrl == null) {
                    setViewVisibility(
                        R.id.widgetMediaPlaceholder,
                        View.VISIBLE,
                    )
                    setViewVisibility(
                        R.id.widgetMediaImage,
                        View.GONE,
                    )
                } else {
                    setViewVisibility(
                        R.id.widgetMediaImage,
                        View.VISIBLE,
                    )
                    setViewVisibility(
                        R.id.widgetMediaPlaceholder,
                        View.GONE,
                    )
                    Timber.d("Fetching media preview image")
                    Handler(Looper.getMainLooper()).post {
                        try {
                            val request = ImageRequest.Builder(context)
                                .data(url)
                                .target(RemoteViewsTarget(context, appWidgetId, this, R.id.widgetMediaImage))
                                .size(1024)
                                .build()
                            context.imageLoader.enqueue(request)
                        } catch (e: Exception) {
                            Timber.e(e, "Unable to load image")
                        }
                    }
                }

                setOnClickPendingIntent(
                    R.id.widgetMediaImage,
                    PendingIntent.getBroadcast(
                        context,
                        appWidgetId,
                        updateMediaIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
                setOnClickPendingIntent(
                    R.id.widgetMediaPlaceholder,
                    PendingIntent.getBroadcast(
                        context,
                        appWidgetId,
                        updateMediaIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
                setOnClickPendingIntent(
                    R.id.widgetPlayPauseButton,
                    PendingIntent.getBroadcast(
                        context,
                        appWidgetId,
                        playPauseIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )

                if (showVolume) {
                    setOnClickPendingIntent(
                        R.id.widgetVolumeDownButton,
                        PendingIntent.getBroadcast(
                            context,
                            appWidgetId,
                            volumeDownIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        ),
                    )
                    setOnClickPendingIntent(
                        R.id.widgetVolumeUpButton,
                        PendingIntent.getBroadcast(
                            context,
                            appWidgetId,
                            volumeUpIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        ),
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
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        ),
                    )
                    setOnClickPendingIntent(
                        R.id.widgetNextTrackButton,
                        PendingIntent.getBroadcast(
                            context,
                            appWidgetId,
                            nextTrackIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        ),
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
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        ),
                    )
                    setOnClickPendingIntent(
                        R.id.widgetFastForwardButton,
                        PendingIntent.getBroadcast(
                            context,
                            appWidgetId,
                            fastForwardIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        ),
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

    override suspend fun getAllWidgetIdsWithEntities(context: Context): Map<Int, Pair<Int, List<String>>> =
        dao.getAll().associate { it.id to (it.serverId to it.entityId.split(",")) }

    private suspend fun getEntity(
        context: Context,
        serverId: Int,
        entityIds: List<String>,
        suggestedEntity: Entity?,
    ): Entity? {
        val entity: Entity?
        try {
            entity = if (suggestedEntity != null && entityIds.contains(suggestedEntity.entityId)) {
                suggestedEntity
            } else {
                val entities: LinkedList<Entity?> = LinkedList()
                entityIds.forEach {
                    val e = serverManager.integrationRepository(serverId).getEntity(it)
                    if (e?.state == "playing") return e
                    entities.add(e)
                }
                return entities[0]
            }
        } catch (e: Exception) {
            Timber.d("Failed to fetch entity or entity does not exist")
            if (lastIntent == UPDATE_MEDIA_IMAGE) {
                Toast.makeText(context, commonR.string.widget_entity_fetch_error, Toast.LENGTH_LONG).show()
            }
            return null
        }

        return entity
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
            UPDATE_VIEW -> updateView(
                context,
                appWidgetId,
            )
            UPDATE_WIDGETS -> {
                super.onScreenOn(context)
            }
            UPDATE_MEDIA_IMAGE -> updateView(context, appWidgetId)
            CALL_PREV_TRACK -> callPreviousTrackAction(context, appWidgetId)
            CALL_REWIND -> callRewindAction(context, appWidgetId)
            CALL_PLAYPAUSE -> callPlayPauseAction(context, appWidgetId)
            CALL_FASTFORWARD -> callFastForwardAction(context, appWidgetId)
            CALL_NEXT_TRACK -> callNextTrackAction(context, appWidgetId)
            CALL_VOLUME_DOWN -> callVolumeDownAction(context, appWidgetId)
            CALL_VOLUME_UP -> callVolumeUpAction(context, appWidgetId)
        }
    }

    override suspend fun onEntityStateChanged(context: Context, appWidgetId: Int, entity: Entity) {
        dao.get(appWidgetId)?.let {
            widgetScope?.launch {
                val views =
                    getWidgetRemoteViews(
                        context,
                        appWidgetId,
                        getEntity(context, it.serverId, it.entityId.split(","), null),
                    )
                AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
            }
        }
    }

    private fun callPreviousTrackAction(context: Context, appWidgetId: Int) {
        widgetScope?.launch {
            Timber.d("Retrieving media player entity for app widget $appWidgetId")
            val entity: MediaPlayerControlsWidgetEntity? = dao.get(appWidgetId)

            if (entity == null) {
                Timber.d("Failed to retrieve media player entity")
                return@launch
            }

            Timber.d(
                "Calling previous track action:" + System.lineSeparator() +
                    "entity id: " + entity.entityId + System.lineSeparator(),
            )

            val action = "media_previous_track"
            val entityId: String = getEntity(
                context,
                entity.serverId,
                entity.entityId.split(","),
                null,
            )?.entityId.toString()

            val actionDataMap: HashMap<String, Any> = hashMapOf("entity_id" to entityId)

            serverManager.integrationRepository().callAction(MEDIA_PLAYER_DOMAIN, action, actionDataMap)
        }
    }

    private fun callRewindAction(context: Context, appWidgetId: Int) {
        widgetScope?.launch {
            Timber.d("Retrieving media player entity for app widget $appWidgetId")
            val entity: MediaPlayerControlsWidgetEntity? = dao.get(appWidgetId)

            if (entity == null) {
                Timber.d("Failed to retrieve media player entity")
                return@launch
            }

            Timber.d(
                "Calling rewind action:" + System.lineSeparator() +
                    "entity id: " + entity.entityId + System.lineSeparator(),
            )

            val currentEntityInfo = try {
                serverManager.integrationRepository(entity.serverId).getEntity(entity.entityId)
            } catch (e: Exception) {
                null
            }
            if (currentEntityInfo == null) {
                Timber.d("Failed to fetch entity or entity does not exist")
                if (lastIntent != Intent.ACTION_SCREEN_ON) {
                    Toast.makeText(context, commonR.string.widget_entity_fetch_error, Toast.LENGTH_LONG).show()
                }
                return@launch
            }

            val fetchedAttributes = currentEntityInfo.attributes
            val currentTime = fetchedAttributes["media_position"]?.toString()?.toDoubleOrNull()

            if (currentTime == null) {
                Timber.d("Failed to get entity current time, aborting call")
                return@launch
            }

            val action = "media_seek"
            val entityId: String = getEntity(
                context,
                entity.serverId,
                entity.entityId.split(","),
                null,
            )?.entityId.toString()

            val actionDataMap: HashMap<String, Any> = hashMapOf(
                "entity_id" to entityId,
                "seek_position" to currentTime - 10,
            )

            try {
                serverManager.integrationRepository(
                    entity.serverId,
                ).callAction(MEDIA_PLAYER_DOMAIN, action, actionDataMap)
            } catch (e: Exception) {
                Timber.e(e, "Exception calling rewind action")
            }
        }
    }

    private fun callPlayPauseAction(context: Context, appWidgetId: Int) {
        widgetScope?.launch {
            Timber.d("Retrieving media player entity for app widget $appWidgetId")
            val entity: MediaPlayerControlsWidgetEntity? = dao.get(appWidgetId)

            if (entity == null) {
                Timber.d("Failed to retrieve media player entity")
                return@launch
            }

            Timber.d(
                "Calling play/pause action:" + System.lineSeparator() +
                    "entity id: " + entity.entityId + System.lineSeparator(),
            )

            val action = "media_play_pause"
            val entityId: String = getEntity(
                context,
                entity.serverId,
                entity.entityId.split(","),
                null,
            )?.entityId.toString()

            val actionDataMap: HashMap<String, Any> = hashMapOf("entity_id" to entityId)

            try {
                serverManager.integrationRepository(
                    entity.serverId,
                ).callAction(MEDIA_PLAYER_DOMAIN, action, actionDataMap)
            } catch (e: Exception) {
                Timber.e(e, "Exception calling play pause action")
            }
        }
    }

    private fun callFastForwardAction(context: Context, appWidgetId: Int) {
        widgetScope?.launch {
            Timber.d("Retrieving media player entity for app widget $appWidgetId")
            val entity: MediaPlayerControlsWidgetEntity? = dao.get(appWidgetId)

            if (entity == null) {
                Timber.d("Failed to retrieve media player entity")
                return@launch
            }

            Timber.d(
                "Calling fast forward action:" + System.lineSeparator() +
                    "entity id: " + entity.entityId + System.lineSeparator(),
            )

            val currentEntityInfo = try {
                serverManager.integrationRepository(entity.serverId).getEntity(entity.entityId)
            } catch (e: Exception) {
                null
            }
            if (currentEntityInfo == null) {
                Timber.d("Failed to fetch entity or entity does not exist")
                if (lastIntent != Intent.ACTION_SCREEN_ON) {
                    Toast.makeText(context, commonR.string.widget_entity_fetch_error, Toast.LENGTH_LONG).show()
                }
                return@launch
            }

            val fetchedAttributes = currentEntityInfo.attributes
            val currentTime = fetchedAttributes["media_position"]?.toString()?.toDoubleOrNull()

            if (currentTime == null) {
                Timber.d("Failed to get entity current time, aborting call")
                return@launch
            }

            val action = "media_seek"
            val entityId: String = getEntity(
                context,
                entity.serverId,
                entity.entityId.split(","),
                null,
            )?.entityId.toString()

            val actionDataMap: HashMap<String, Any> = hashMapOf(
                "entity_id" to entityId,
                "seek_position" to currentTime + 10,
            )

            try {
                serverManager.integrationRepository(
                    entity.serverId,
                ).callAction(MEDIA_PLAYER_DOMAIN, action, actionDataMap)
            } catch (e: Exception) {
                Timber.e(e, "Exception calling fast forward action")
            }
        }
    }

    private fun callNextTrackAction(context: Context, appWidgetId: Int) {
        widgetScope?.launch {
            Timber.d("Retrieving media player entity for app widget $appWidgetId")
            val entity: MediaPlayerControlsWidgetEntity? = dao.get(appWidgetId)

            if (entity == null) {
                Timber.d("Failed to retrieve media player entity")
                return@launch
            }

            Timber.d(
                "Calling next track action:" + System.lineSeparator() +
                    "entity id: " + entity.entityId + System.lineSeparator(),
            )

            val action = "media_next_track"
            val entityId: String = getEntity(
                context,
                entity.serverId,
                entity.entityId.split(","),
                null,
            )?.entityId.toString()

            val actionDataMap: HashMap<String, Any> = hashMapOf("entity_id" to entityId)

            try {
                serverManager.integrationRepository(
                    entity.serverId,
                ).callAction(MEDIA_PLAYER_DOMAIN, action, actionDataMap)
            } catch (e: Exception) {
                Timber.e(e, "Exception calling next track action")
            }
        }
    }

    private fun callVolumeDownAction(context: Context, appWidgetId: Int) {
        widgetScope?.launch {
            Timber.d("Retrieving media player entity for app widget $appWidgetId")
            val entity: MediaPlayerControlsWidgetEntity? = dao.get(appWidgetId)

            if (entity == null) {
                Timber.d("Failed to retrieve media player entity")
                return@launch
            }

            Timber.d(
                "Calling volume down action:" + System.lineSeparator() +
                    "entity id: " + entity.entityId + System.lineSeparator(),
            )

            val action = "volume_down"
            val entityId: String = getEntity(
                context,
                entity.serverId,
                entity.entityId.split(","),
                null,
            )?.entityId.toString()

            val actionDataMap: HashMap<String, Any> = hashMapOf("entity_id" to entityId)

            try {
                serverManager.integrationRepository(
                    entity.serverId,
                ).callAction(MEDIA_PLAYER_DOMAIN, action, actionDataMap)
            } catch (e: Exception) {
                Timber.e(e, "Exception calling volume down action")
            }
        }
    }

    private fun callVolumeUpAction(context: Context, appWidgetId: Int) {
        widgetScope?.launch {
            Timber.d("Retrieving media player entity for app widget $appWidgetId")
            val entity: MediaPlayerControlsWidgetEntity? = dao.get(appWidgetId)

            if (entity == null) {
                Timber.d("Failed to retrieve media player entity")
                return@launch
            }

            Timber.d(
                "Calling volume up action:" + System.lineSeparator() +
                    "entity id: " + entity.entityId + System.lineSeparator(),
            )

            val action = "volume_up"
            val entityId: String = getEntity(
                context,
                entity.serverId,
                entity.entityId.split(","),
                null,
            )?.entityId.toString()

            val actionDataMap: HashMap<String, Any> = hashMapOf("entity_id" to entityId)

            try {
                serverManager.integrationRepository(
                    entity.serverId,
                ).callAction(MEDIA_PLAYER_DOMAIN, action, actionDataMap)
            } catch (e: Exception) {
                Timber.e(e, "Exception calling volume up action")
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        widgetScope?.launch {
            dao.deleteAll(appWidgetIds)
            appWidgetIds.forEach { removeSubscription(it) }
        }
    }
}
