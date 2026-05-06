package io.homeassistant.companion.android.mediacontrol

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap.CompressFormat
import android.graphics.BitmapFactory
import android.os.Looper
import androidx.annotation.OptIn
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
import io.homeassistant.companion.android.launch.LaunchActivity
import io.homeassistant.companion.android.util.sensitive
import java.io.ByteArrayOutputStream
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
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
    /** Stable identifier for this session, derived from the entity config. */
    val id: String = "${config.serverId}:${config.entityId}"

    private var mediaSession: MediaSession? = null

    /** True if the player is currently playing and has at least one media item. */
    val isPlaying: Boolean
        get() = mediaSession?.player?.let { it.playWhenReady && it.mediaItemCount > 0 } == true

    /** True if the player has at least one media item (playing or paused). */
    val hasActiveMedia: Boolean
        get() = mediaSession?.player?.let { it.mediaItemCount > 0 } == true

    /**
     * Unregisters this session from [service] by calling
     * [MediaSessionService.removeSession]. Has no effect if the session is not currently active.
     */
    fun unregisterFrom(service: MediaSessionService) {
        mediaSession?.let { service.removeSession(it) }
    }

    /**
     * Builds a [MediaStyle][MediaStyleNotificationHelper.MediaStyle] notification for this session
     * using the player's current metadata (title, artist, artwork).
     *
     * @return The notification, or null if the session is not currently active.
     */
    @OptIn(UnstableApi::class)
    fun buildNotification(): Notification? {
        val session = mediaSession ?: return null
        val metadata = session.player.mediaMetadata
        val artworkBitmap = metadata.artworkData?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        return NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setStyle(MediaStyleNotificationHelper.MediaStyle(session))
            .setSmallIcon(commonR.drawable.ic_stat_ic_notification)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setContentTitle(metadata.title ?: id)
            .setContentText(metadata.artist)
            .setLargeIcon(artworkBitmap)
            .setOngoing(session.player.isPlaying)
            .setContentIntent(session.sessionActivity)
            .build()
    }

    private fun getCommandCallback(scope: CoroutineScope, onCommandComplete: () -> Unit) =
        object : HaRemoteMediaPlayer.CommandCallback {
            override fun onPlayRequested() = scope.launch {
                callMediaAction(ACTION_MEDIA_PLAY)
                onCommandComplete()
            }

            override fun onPauseRequested() = scope.launch {
                callMediaAction(ACTION_MEDIA_PAUSE)
                onCommandComplete()
            }

            override fun onSeekRequested(positionMs: Long) = scope.launch {
                callMediaAction(
                    action = ACTION_MEDIA_SEEK,
                    extraData = mapOf("seek_position" to positionMs / 1000.0),
                )
                onCommandComplete()
            }

            override fun onNextRequested() = scope.launch {
                callMediaAction(ACTION_MEDIA_NEXT_TRACK)
                onCommandComplete()
            }

            override fun onPreviousRequested() = scope.launch {
                callMediaAction(ACTION_MEDIA_PREVIOUS_TRACK)
                onCommandComplete()
            }

            override fun onSetVolumeRequested(volume: Float) = scope.launch {
                callMediaAction(
                    action = ACTION_VOLUME_SET,
                    extraData = mapOf("volume_level" to volume),
                )
                onCommandComplete()
            }

            override fun onIncreaseVolumeRequested() = scope.launch {
                callMediaAction(ACTION_VOLUME_UP)
                onCommandComplete()
            }

            override fun onDecreaseVolumeRequested() = scope.launch {
                callMediaAction(ACTION_VOLUME_DOWN)
                onCommandComplete()
            }

            override fun onMuteRequested(muted: Boolean) = scope.launch {
                callMediaAction(
                    action = ACTION_VOLUME_MUTE,
                    extraData = mapOf("is_volume_muted" to muted),
                )
                onCommandComplete()
            }

            override fun onStopRequested() = scope.launch {
                callMediaAction(ACTION_MEDIA_STOP)
                onCommandComplete()
            }

            override fun onShuffleRequested(shuffle: Boolean) = scope.launch {
                callMediaAction(
                    action = ACTION_SHUFFLE_SET,
                    extraData = mapOf("shuffle" to shuffle),
                )
                onCommandComplete()
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
                    onCommandComplete()
                }
            }
        }

    /**
     * Creates the [MediaSession] and player, starts observing entity state, and suspends until
     * the calling coroutine is cancelled. Calls [onSessionReady] with the new session immediately
     * after creation so the caller can register it with
     * [androidx.media3.session.MediaSessionService.addSession].
     *
     * All Media3 resources are released in a `finally` block, so they are always cleaned up
     * regardless of how the coroutine ends (cancellation or normal flow completion).
     */
    suspend fun observe(onSessionReady: suspend (MediaSession) -> Unit) {
        coroutineScope {
            FailFast.failWhen(mediaSession != null) {
                "observe() called while a session is already active for ${config.entityId}"
            }
            Timber.d("observe: starting for ${config.entityId}")

            var observationJob = launch { startObservingState() }

            // After each command, restart observation if the WebSocket flow has completed (e.g.
            // after a transient disconnect). This lets the user resume control without reopening
            // the app.
            fun restartObservationIfNeeded() {
                if (!observationJob.isActive) {
                    Timber.d("observe: restarting observation after command for ${config.entityId}")
                    observationJob = launch { startObservingState() }
                }
            }

            val player =
                HaRemoteMediaPlayer(Looper.getMainLooper(), getCommandCallback(this, ::restartObservationIfNeeded))
            val session = buildMediaSession(player)
            mediaSession = session
            try {
                onSessionReady(session)
                awaitCancellation()
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
    }

    /**
     * Observes entity state for [config] until the flow completes or the coroutine is cancelled.
     * The flow completes when the WebSocket subscription returns null (not yet connected), and
     * is cancelled when the WebSocket disconnects (the backing SharedFlow's scope is cancelled).
     * In both cases the session is not restarted here; reconnection happens when the user opens
     * the app, which recreates active sessions via [HaMediaSessionService].
     */
    private suspend fun startObservingState() {
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
        .setId("${config.serverId}:${config.entityId}")
        .setCallback(MediaSessionCallback())
        .build()
        .also { session ->
            /**
             * FLAG_ACTIVITY_NEW_TASK is required when starting an activity from a service context
             * (PendingIntents from notifications always fire in a non-Activity context).
             * FLAG_ACTIVITY_SINGLE_TOP prevents stacking a redundant WebViewActivity if one is
             * already at the top; onNewIntent delivers the path to the existing instance instead.
             */
            val tapIntent = LaunchActivity.newInstance(
                context = context,
                deepLink = LaunchActivity.DeepLink.NavigateTo(
                    path = "entityId:${config.entityId}",
                    serverId = config.serverId,
                ),
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
            serverManager.integrationRepository(config.serverId)
                .callAction(MEDIA_PLAYER_DOMAIN, action, actionData)
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
        val pictureUrl = state.entityPictureUrl
        val updatedCache = when {
            pictureUrl == null -> ArtworkCache()
            pictureUrl == cache.url -> cache
            else -> {
                val bytes = resolveArtworkUrl(state)?.let { loadBitmapAsPng(it) }
                if (bytes != null) ArtworkCache(url = pictureUrl, bytes = bytes) else cache
            }
        }

        withContext(Dispatchers.Main) {
            mediaSession?.player?.let { player ->
                (player as? HaRemoteMediaPlayer)?.updateState(state = state, artworkPngBytes = updatedCache.bytes)
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
            Timber.e(e, "Failed to resolve artwork base URL for server ${state.serverId}")
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
                .size(NOTIFICATION_ICON_SIZE_PX, NOTIFICATION_ICON_SIZE_PX)
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

    companion object {
        /** Notification channel ID used for all media control notifications. */
        const val NOTIFICATION_CHANNEL_ID = "media_session"

        /** Target pixel size for notification large icon artwork. Pre-scaling in Coil avoids
         * main-thread downscaling by Android's Icon class (StrictMode CustomViolation). */
        private const val NOTIFICATION_ICON_SIZE_PX = 256

        private const val ACTION_MEDIA_PLAY = "media_play"
        private const val ACTION_MEDIA_PAUSE = "media_pause"
        private const val ACTION_MEDIA_STOP = "media_stop"
        private const val ACTION_MEDIA_SEEK = "media_seek"
        private const val ACTION_MEDIA_NEXT_TRACK = "media_next_track"
        private const val ACTION_MEDIA_PREVIOUS_TRACK = "media_previous_track"
        private const val ACTION_VOLUME_SET = "volume_set"
        private const val ACTION_VOLUME_UP = "volume_up"
        private const val ACTION_VOLUME_DOWN = "volume_down"
        private const val ACTION_VOLUME_MUTE = "volume_mute"
        private const val ACTION_SHUFFLE_SET = "shuffle_set"
        private const val ACTION_REPEAT_SET = "repeat_set"
    }

    /** Creates [HaMediaSession] instances with the runtime-provided [config]. */
    @AssistedFactory
    interface Factory {
        fun create(config: MediaControlEntityConfig): HaMediaSession
    }
}
