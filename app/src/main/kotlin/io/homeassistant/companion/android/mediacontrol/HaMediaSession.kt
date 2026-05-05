package io.homeassistant.companion.android.mediacontrol

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap.CompressFormat
import android.graphics.BitmapFactory
import android.os.Looper
import androidx.annotation.OptIn
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.MEDIA_PLAYER_DOMAIN
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlEntityConfig
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlRepository
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlState
import io.homeassistant.companion.android.common.data.mediacontrol.MediaPlaybackState
import io.homeassistant.companion.android.common.data.mediacontrol.MediaRepeatMode
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.firstUrlOrNull
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.util.sensitive
import io.homeassistant.companion.android.webview.WebViewActivity
import java.io.ByteArrayOutputStream
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Owns the [MediaSession] and [HaRemoteMediaPlayer] for a single Home Assistant media_player entity.
 *
 * Observes [MediaControlRepository] for entity state changes, loads artwork via Coil, and
 * translates Media3 player commands into Home Assistant service calls via [ServerManager].
 *
 * Call [observe] to start the session. The session and its Media3 resources are created when
 * [observe] is called and released automatically when the calling coroutine is cancelled.
 *
 * @param context Application context used for Coil image loading and [MediaSession] construction.
 * @param config Identifies the media_player entity this session represents.
 * @param mediaControlRepository Provides the per-entity state flow.
 * @param serverManager Used to resolve artwork base URLs and call HA integration actions.
 */
@OptIn(UnstableApi::class)
class HaMediaSession @AssistedInject constructor(
    @ApplicationContext private val context: Context,
    @Assisted private val config: MediaControlEntityConfig,
    private val mediaControlRepository: MediaControlRepository,
    private val serverManager: ServerManager,
) {
    /**
     * Stable identifier for this session, derived from [config]. Matches the [MediaSession.id]
     * assigned in [buildMediaSession].
     */
    val id: String get() = "${config.serverId}_${config.entityId}"

    /**
     * The active [MediaSession] while [observe] is running, null otherwise.
     * Internal so that unit tests can access the player to trigger commands and assert state.
     */
    @VisibleForTesting
    internal var mediaSession: MediaSession? = null
        private set

    /**
     * Whether the session is currently playing media. Returns false when [observe] is not running
     * or the player has no media content loaded.
     */
    val isPlaying: Boolean
        get() = mediaSession?.player?.let { it.playWhenReady && it.mediaItemCount > 0 } == true

    /**
     * Whether the session has media content loaded (i.e. the player's media item count is > 0).
     * Returns false when [observe] is not running.
     */
    val hasMediaContent: Boolean
        get() = mediaSession?.player?.mediaItemCount?.let { it > 0 } == true

    private fun getCommandCallback(scope: CoroutineScope) = object : HaRemoteMediaPlayer.CommandCallback {
        override fun onPlayRequested() = scope.launch { callMediaAction(ACTION_MEDIA_PLAY) }

        override fun onPauseRequested() = scope.launch { callMediaAction(ACTION_MEDIA_PAUSE) }

        override fun onSeekRequested(positionMs: Long) = scope.launch {
            callMediaAction(
                action = ACTION_MEDIA_SEEK,
                extraData = mapOf("seek_position" to positionMs / 1000.0),
            )
        }

        override fun onNextRequested() = scope.launch { callMediaAction(ACTION_MEDIA_NEXT_TRACK) }

        override fun onPreviousRequested() = scope.launch { callMediaAction(ACTION_MEDIA_PREVIOUS_TRACK) }

        override fun onSetVolumeRequested(volume: Float) = scope.launch {
            callMediaAction(
                action = ACTION_VOLUME_SET,
                extraData = mapOf("volume_level" to volume),
            )
        }

        override fun onIncreaseVolumeRequested() = scope.launch { callMediaAction(ACTION_VOLUME_UP) }

        override fun onDecreaseVolumeRequested() = scope.launch { callMediaAction(ACTION_VOLUME_DOWN) }

        override fun onMuteRequested(muted: Boolean) = scope.launch {
            callMediaAction(
                action = ACTION_VOLUME_MUTE,
                extraData = mapOf("is_volume_muted" to muted),
            )
        }

        override fun onStopRequested() = scope.launch { callMediaAction(ACTION_MEDIA_STOP) }

        override fun onShuffleRequested(shuffle: Boolean) = scope.launch {
            callMediaAction(
                action = ACTION_SHUFFLE_SET,
                extraData = mapOf("shuffle" to shuffle),
            )
        }

        override fun onRepeatRequested(repeatMode: MediaRepeatMode): Job {
            val haRepeatValue = when (repeatMode) {
                is MediaRepeatMode.Off -> "off"
                is MediaRepeatMode.One -> "one"
                is MediaRepeatMode.All -> "all"
            }
            return scope.launch {
                callMediaAction(
                    action = ACTION_REPEAT_SET,
                    extraData = mapOf("repeat" to haRepeatValue),
                )
            }
        }
    }

    /**
     * Registers this session with [service] so that Media3's internal notification manager
     * can monitor player state and call [MediaSessionService.onUpdateNotification]. Has no effect
     * if [observe] is not currently running.
     */
    fun addToService(service: MediaSessionService) {
        mediaSession?.let { service.addSession(it) }
    }

    /**
     * Removes this session from [service]. Has no effect if [observe] is not currently running.
     */
    fun removeFromService(service: MediaSessionService) {
        mediaSession?.let { service.removeSession(it) }
    }

    /**
     * Builds a [MediaStyleNotificationHelper.MediaStyle] notification for this session using the
     * player's current metadata (title, artist, artwork). Returns null when [observe] is not
     * running or the session has no media content.
     *
     * @param channelId The notification channel ID to assign to the notification.
     */
    @OptIn(UnstableApi::class)
    fun buildNotification(channelId: String): Notification? {
        val session = mediaSession ?: return null
        val metadata = session.player.mediaMetadata
        val artworkBitmap = metadata.artworkData?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        return NotificationCompat.Builder(context, channelId)
            .setStyle(MediaStyleNotificationHelper.MediaStyle(session))
            .setSmallIcon(commonR.drawable.ic_stat_ic_notification)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setContentTitle(metadata.title ?: session.id)
            .setContentText(metadata.artist)
            .setLargeIcon(artworkBitmap)
            .setOngoing(session.player.isPlaying)
            .setContentIntent(session.sessionActivity)
            .build()
    }

    /**
     * Creates the [MediaSession] and player, starts observing entity state, and suspends until
     * the calling coroutine is cancelled. Calls [onSessionReady] with this [HaMediaSession]
     * immediately after the [MediaSession] is created so the caller can register it with
     * [addToService].
     *
     * All Media3 resources are released in a `finally` block, so they are always cleaned up
     * regardless of how the coroutine ends (cancellation or normal flow completion).
     */
    suspend fun observe(onSessionReady: suspend (HaMediaSession) -> Unit) = coroutineScope {
        FailFast.failWhen(mediaSession != null) {
            "observe() called while a session is already active for ${config.entityId}"
        }
        Timber.d("observe: starting for ${config.entityId}")
        val player = HaRemoteMediaPlayer(Looper.getMainLooper(), getCommandCallback(this))
        val session = buildMediaSession(player)
        mediaSession = session
        try {
            onSessionReady(this@HaMediaSession)
            startObservingState()
            Timber.d(
                "observe: startObservingState returned normally for ${config.entityId} (flow completed or WebSocket returned null)",
            )
        } catch (e: CancellationException) {
            Timber.d("observe: cancelled for ${config.entityId}")
            throw e
        } finally {
            Timber.d("observe: finally block running for ${config.entityId}, releasing player and session")
            mediaSession = null
            withContext(NonCancellable + Dispatchers.Main) {
                player.release()
                session.release()
            }
        }
    }

    /**
     * Observes entity state for [config] until the flow completes or the coroutine is cancelled.
     * The flow completes when the WebSocket subscription returns null (not yet connected), and
     * is cancelled when the WebSocket disconnects (the backing SharedFlow's scope is cancelled).
     * In both cases the session is not restarted here; reconnection happens when the user opens
     * the app, which recreates active sessions via [HaMediaSessionService].
     */
    internal suspend fun startObservingState() {
        Timber.d("startObservingState: starting for ${config.entityId}")
        var artworkCache = ArtworkCache()
        mediaControlRepository.observeEntityState(config).collect { state ->
            if (state == null) {
                Timber.d("startObservingState: received null state for ${config.entityId}, skipping update")
                return@collect
            }
            Timber.d("startObservingState: received state for ${config.entityId}, playbackState=${state.playbackState}")
            if (state.playbackState is MediaPlaybackState.Off) {
                // Entity is off: reset the player to idle (no playlist, no commands) so Media3
                // does not create a notification for this session. A notification for an idle
                // session with no content would replace the foreground notification of any
                // currently-playing session (e.g. another configured entity), hiding its control.
                artworkCache = ArtworkCache()
                withContext(Dispatchers.Main) {
                    mediaSession?.player?.let {
                        (it as? HaRemoteMediaPlayer)?.updateState(state = null, artworkPngBytes = null)
                    }
                }
            } else {
                artworkCache = loadArtworkAndUpdatePlayer(state, artworkCache)
            }
        }
        Timber.d("startObservingState: flow collection ended for ${config.entityId}")
    }

    private fun buildMediaSession(player: HaRemoteMediaPlayer): MediaSession = MediaSession.Builder(context, player)
        .setId("${config.serverId}_${config.entityId}")
        .setCallback(MediaSessionCallback())
        .build()
        .also { session ->
            /**
             * FLAG_ACTIVITY_NEW_TASK is required when starting an activity from a service context
             * (PendingIntents from notifications always fire in a non-Activity context).
             * FLAG_ACTIVITY_SINGLE_TOP prevents stacking a redundant WebViewActivity if one is
             * already at the top; onNewIntent delivers the path to the existing instance instead.
             */
            val tapIntent = WebViewActivity.newInstance(
                context = context,
                path = "entityId:${config.entityId}",
                serverId = config.serverId,
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            session.sessionActivity = PendingIntent.getActivity(
                context,
                "${config.serverId}:${config.entityId}".hashCode(),
                tapIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

    private suspend fun callMediaAction(action: String, extraData: Map<String, Any> = emptyMap()) {
        val actionData = hashMapOf<String, Any>("entity_id" to config.entityId)
        actionData.putAll(extraData)
        try {
            withContext(Dispatchers.IO) {
                serverManager.integrationRepository(config.serverId)
                    .callAction(MEDIA_PLAYER_DOMAIN, action, actionData)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to call media action $action on ${config.entityId}")
        }
    }

    /**
     * Loads artwork for [state] if the URL has changed, then updates the player on the main thread.
     *
     * @return An updated [ArtworkCache] reflecting the outcome of the load attempt.
     */
    private suspend fun loadArtworkAndUpdatePlayer(state: MediaControlState, cache: ArtworkCache): ArtworkCache {
        val rawPictureUrl = state.entityPictureUrl
        val (updatedCache, pngBytes) = when {
            rawPictureUrl != null && rawPictureUrl != cache.url -> {
                val resolvedUrl = resolveArtworkUrl(state)
                val bytes = resolvedUrl?.let { loadBitmapAsPng(it) }
                if (bytes != null) {
                    ArtworkCache(url = rawPictureUrl, bytes = bytes) to bytes
                } else {
                    cache to cache.bytes
                }
            }
            rawPictureUrl == null -> {
                ArtworkCache() to null
            }
            else -> cache to cache.bytes
        }

        withContext(Dispatchers.Main) {
            mediaSession?.player?.let { player ->
                (player as? HaRemoteMediaPlayer)?.updateState(state = state, artworkPngBytes = pngBytes)
            }
        }
        return updatedCache
    }

    private suspend fun resolveArtworkUrl(state: MediaControlState): String? {
        val entityPictureUrl = state.entityPictureUrl ?: return null
        if (entityPictureUrl.startsWith("http")) return entityPictureUrl

        val baseUrl = try {
            serverManager.connectionStateProvider(state.serverId)
                .urlFlow()
                .firstUrlOrNull()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to resolve artwork URL for server ${state.serverId}")
            null
        } ?: return null

        return URL(baseUrl, entityPictureUrl).toString()
    }

    /** Loads album art and compresses to PNG bytes on the IO dispatcher. */
    private suspend fun loadBitmapAsPng(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .build()
            val result = context.imageLoader.execute(request)
            result.image?.toBitmap()?.let { bitmap ->
                val stream = ByteArrayOutputStream()
                bitmap.compress(CompressFormat.PNG, 100, stream)
                stream.toByteArray()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to load album art from ${sensitive(url)}")
            null
        }
    }

    /**
     * Restricts media session connections to trusted controllers (same app, system,
     * or apps with MEDIA_CONTENT_CONTROL / notification listener access).
     */
    @OptIn(UnstableApi::class)
    private class MediaSessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            if (!controller.isTrusted) {
                Timber.w("Rejecting connection from untrusted media controller package=${controller.packageName}")
                return MediaSession.ConnectionResult.reject()
            }
            return MediaSession.ConnectionResult.accept(
                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS,
                MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS,
            )
        }
    }

    /** Immutable cache of the last successfully loaded artwork. */
    private data class ArtworkCache(val url: String? = null, val bytes: ByteArray? = null)

    private companion object {
        const val ACTION_MEDIA_PLAY = "media_play"
        const val ACTION_MEDIA_PAUSE = "media_pause"
        const val ACTION_MEDIA_STOP = "media_stop"
        const val ACTION_MEDIA_SEEK = "media_seek"
        const val ACTION_MEDIA_NEXT_TRACK = "media_next_track"
        const val ACTION_MEDIA_PREVIOUS_TRACK = "media_previous_track"
        const val ACTION_VOLUME_SET = "volume_set"
        const val ACTION_VOLUME_UP = "volume_up"
        const val ACTION_VOLUME_DOWN = "volume_down"
        const val ACTION_VOLUME_MUTE = "volume_mute"
        const val ACTION_SHUFFLE_SET = "shuffle_set"
        const val ACTION_REPEAT_SET = "repeat_set"
    }

    /** Creates [HaMediaSession] instances with the runtime-provided [config]. */
    @AssistedFactory
    interface Factory {
        fun create(config: MediaControlEntityConfig): HaMediaSession
    }
}
