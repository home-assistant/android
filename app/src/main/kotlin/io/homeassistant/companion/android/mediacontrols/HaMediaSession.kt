package io.homeassistant.companion.android.mediacontrols

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.os.Looper
import androidx.annotation.MainThread
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.graphics.scale
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Precision
import coil3.toBitmap
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.EntityExt
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.MEDIA_PLAYER_DOMAIN
import io.homeassistant.companion.android.common.data.integration.MediaPlaybackState
import io.homeassistant.companion.android.common.data.integration.MediaRepeatMode
import io.homeassistant.companion.android.common.data.integration.display.EntitiesForDisplayManager
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithoutContext
import io.homeassistant.companion.android.common.data.mediacontrols.MediaControlsEntityConfig
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.firstUrlOrNull
import io.homeassistant.companion.android.common.util.CHANNEL_MEDIA_SESSION
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.frontend.navigation.FrontendTarget
import io.homeassistant.companion.android.launch.LaunchActivity
import io.homeassistant.companion.android.mediacontrols.HaMediaSession.Companion.MAX_ARTWORK_SIZE
import io.homeassistant.companion.android.util.sensitive
import java.io.ByteArrayOutputStream
import java.net.URL
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Owns the [MediaSession] and [HaRemoteMediaPlayer] for a single Home Assistant media_player entity.
 *
 * Observes [EntitiesForDisplayManager] for entity state changes, loads artwork via Coil, and
 * translates Media3 player commands into Home Assistant service calls via [ServerManager].
 *
 * Call [observe] to start the session. The session and its Media3 resources are created when
 * [observe] is called and released automatically when the calling coroutine is cancelled.
 *
 * @param context Application context.
 * @param config Identifies the media_player entity this session represents.
 * @param entitiesForDisplayManager Resolves and follows the state of the entity.
 * @param serverManager Used to resolve artwork base URLs and call HA integration actions.
 */
@OptIn(UnstableApi::class, ExperimentalTime::class)
class HaMediaSession @AssistedInject constructor(
    @ApplicationContext private val context: Context,
    @Assisted private val config: MediaControlsEntityConfig,
    private val entitiesForDisplayManager: EntitiesForDisplayManager,
    private val serverManager: ServerManager,
    private val clock: Clock,
) {
    /** Stable identifier for this session. Delegates to [MediaControlsEntityConfig.id]. */
    val id: String get() = config.id

    /** Non-null while [observe] is running. */
    @get:MainThread
    @set:MainThread
    internal var mediaSession: MediaSession? = null
        private set

    @get:MainThread
    @set:MainThread
    private var notificationArtwork: Bitmap? = null

    @get:MainThread
    @set:MainThread
    private var notificationEntityName: String? = null

    /** @return `true` if the player is currently playing and has at least one media item. */
    @get:MainThread
    val isPlaying: Boolean
        get() = mediaSession?.player?.let { it.playWhenReady && it.mediaItemCount > 0 } == true

    /** @return `true` if the player has at least one media item (playing or paused). */
    @get:MainThread
    val hasActiveMedia: Boolean
        get() = mediaSession?.player?.let { it.mediaItemCount > 0 } == true

    /**
     * Builds a [MediaStyle][MediaStyleNotificationHelper.MediaStyle] notification for this session
     * using the player's current metadata (title, artist, artwork).
     *
     * @return The notification, or null if the session is not currently active.
     */
    @MainThread
    fun buildNotification(): Notification? {
        val session = mediaSession ?: return null
        session.setMediaButtonPreferences(buildMediaButtonPreferences(session.player))
        val metadata = session.player.mediaMetadata
        return NotificationCompat.Builder(context, CHANNEL_MEDIA_SESSION)
            .setStyle(MediaStyleNotificationHelper.MediaStyle(session))
            .setSmallIcon(commonR.drawable.ic_stat_ic_notification)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setContentTitle(metadata.title ?: notificationEntityName ?: id)
            .setContentText(metadata.artist)
            .setLargeIcon(notificationArtwork)
            .setOngoing(session.player.isPlaying)
            .setContentIntent(session.sessionActivity)
            .build()
    }

    @OptIn(UnstableApi::class)
    private fun buildMediaButtonPreferences(player: Player): List<CommandButton> {
        val buttons = mutableListOf<CommandButton>()
        if (player.isCommandAvailable(Player.COMMAND_SET_SHUFFLE_MODE)) {
            val shuffleOn = player.shuffleModeEnabled
            val shuffleStringRes = if (shuffleOn) {
                commonR.string.media_control_shuffle_on
            } else {
                commonR.string.media_control_shuffle_off
            }
            buttons.add(
                CommandButton.Builder(if (shuffleOn) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF)
                    .setDisplayName(context.getString(shuffleStringRes))
                    .setPlayerCommand(Player.COMMAND_SET_SHUFFLE_MODE, !shuffleOn)
                    .build(),
            )
        }
        if (player.isCommandAvailable(Player.COMMAND_SET_REPEAT_MODE)) {
            val icon = when (player.repeatMode) {
                Player.REPEAT_MODE_ONE -> CommandButton.ICON_REPEAT_ONE
                Player.REPEAT_MODE_ALL -> CommandButton.ICON_REPEAT_ALL
                else -> CommandButton.ICON_REPEAT_OFF
            }
            val nextMode = when (player.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            val displayName = when (player.repeatMode) {
                Player.REPEAT_MODE_ONE -> context.getString(commonR.string.media_control_repeat_one)
                Player.REPEAT_MODE_ALL -> context.getString(commonR.string.media_control_repeat_all)
                else -> context.getString(commonR.string.media_control_repeat_off)
            }
            buttons.add(
                CommandButton.Builder(icon)
                    .setDisplayName(displayName)
                    .setPlayerCommand(Player.COMMAND_SET_REPEAT_MODE, nextMode)
                    .build(),
            )
        }
        return buttons
    }

    private fun getCommandCallback(scope: CoroutineScope) = object : HaRemoteMediaPlayer.CommandCallback {
        override fun onPlayRequested() = scope.launch {
            callMediaAction(ACTION_MEDIA_PLAY)
        }

        override fun onPauseRequested() = scope.launch {
            callMediaAction(ACTION_MEDIA_PAUSE)
        }

        override fun onSeekRequested(positionMs: Long) = scope.launch {
            callMediaAction(
                action = ACTION_MEDIA_SEEK,
                extraData = mapOf("seek_position" to positionMs / 1000.0),
            )
        }

        override fun onNextRequested() = scope.launch {
            callMediaAction(ACTION_MEDIA_NEXT_TRACK)
        }

        override fun onPreviousRequested() = scope.launch {
            callMediaAction(ACTION_MEDIA_PREVIOUS_TRACK)
        }

        override fun onSetVolumeRequested(volume: Float) = scope.launch {
            callMediaAction(
                action = ACTION_VOLUME_SET,
                extraData = mapOf("volume_level" to volume),
            )
        }

        override fun onIncreaseVolumeRequested() = scope.launch {
            callMediaAction(ACTION_VOLUME_UP)
        }

        override fun onDecreaseVolumeRequested() = scope.launch {
            callMediaAction(ACTION_VOLUME_DOWN)
        }

        override fun onMuteRequested(muted: Boolean) = scope.launch {
            callMediaAction(
                action = ACTION_VOLUME_MUTE,
                extraData = mapOf("is_volume_muted" to muted),
            )
        }

        override fun onStopRequested() = scope.launch {
            callMediaAction(ACTION_MEDIA_STOP)
        }

        override fun onShuffleRequested(shuffle: Boolean) = scope.launch {
            callMediaAction(
                action = ACTION_SHUFFLE_SET,
                extraData = mapOf("shuffle" to shuffle),
            )
        }

        override fun onRepeatRequested(repeatMode: MediaRepeatMode): Job {
            val haRepeatValue = when (repeatMode) {
                is MediaRepeatMode.Off -> MEDIA_PLAYER_REPEAT_OFF
                is MediaRepeatMode.One -> EntityExt.MEDIA_PLAYER_REPEAT_ONE
                is MediaRepeatMode.All -> EntityExt.MEDIA_PLAYER_REPEAT_ALL
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

            // Dedicated scope to not block coroutineScope from completing
            // when the entity state flow ends naturally. Cancelled explicitly in the finally block.
            val commandScope = CoroutineScope(
                coroutineContext + SupervisorJob() + CoroutineExceptionHandler { _, e ->
                    Timber.e(e, "Command failed for ${config.entityId}")
                },
            )
            val (player, session) = withContext(Dispatchers.Main) {
                val player = HaRemoteMediaPlayer(Looper.getMainLooper(), getCommandCallback(commandScope), clock)
                val session = buildMediaSession(player)
                mediaSession = session
                player to session
            }
            try {
                onSessionReady(session)
                startObservingState(player)
            } finally {
                commandScope.cancel()
                Timber.d("observe: finally block running for ${config.entityId}, releasing player and session")
                withContext(NonCancellable + Dispatchers.Main) {
                    mediaSession = null
                    notificationArtwork = null
                    notificationEntityName = null
                    player.release()
                    session.release()
                }
            }
        }
    }

    /**
     * Observes entity state for [config] until the flow completes or the coroutine is cancelled.
     *
     * Uses [collectLatest] so that a rapid sequence of state changes cancels any in-flight artwork
     * load for the previous state before processing the new one, preventing a queue of stale
     * artwork fetches from building up. Metadata (title, artist, playback state) is applied
     * immediately on each emission before the IO-bound artwork load begins, so the notification
     * updates without waiting for artwork.
     */
    private suspend fun startObservingState(player: HaRemoteMediaPlayer) {
        Timber.d("startObservingState: starting for ${config.entityId}")
        var artworkCache = ArtworkCache()
        // Emits null while the entity cannot be resolved, and completes when it can no longer be
        // followed, which ends the session
        entitiesForDisplayManager.observe(config.serverId, listOf(config.entityId))
            .map { (it as? EntityDisplayState.Loaded)?.entity(config.entityId) }
            .distinctUntilChanged()
            .onCompletion { cause ->
                Timber.d("startObservingState: ${config.entityId} stopped being followed, cause=$cause")
            }
            .collectLatest { state ->
                if (state == null) {
                    Timber.d("startObservingState: received null state for ${config.entityId}, skipping update")
                    return@collectLatest
                }
                Timber.d(
                    "startObservingState: received state for ${config.entityId}, playback=${state.mediaPlayback?.state}",
                )
                if (state.mediaPlayback?.state is MediaPlaybackState.Off) {
                    // Entity is off: reset the player to idle (no playlist, no commands) so Media3
                    // does not create a notification for this session. A notification for an idle
                    // session with no content would replace the foreground notification of any
                    // currently-playing session (e.g. another configured entity), hiding its control.
                    artworkCache = ArtworkCache()
                    withContext(Dispatchers.Main) {
                        notificationArtwork = null
                        notificationEntityName = null
                        player.updateState(state = null, artworkBytes = null)
                    }
                    return@collectLatest
                }

                // Push metadata and playback state immediately, keeping old artwork bytes in the
                // player until new artwork finishes loading — avoids a blank gap when the URL
                // changes (HA sends multiple updates per track change with different cache= params).
                withContext(Dispatchers.Main) {
                    notificationEntityName = state.name
                    player.updateState(state = state, artworkBytes = artworkCache.bytes)
                }

                when {
                    state.mediaPlayback?.entityPicturePath == null -> {
                        artworkCache = ArtworkCache()
                        withContext(Dispatchers.Main) {
                            notificationArtwork = null
                            player.updateState(state = state, artworkBytes = null)
                        }
                    }

                    state.mediaPlayback?.entityPicturePath != artworkCache.url -> {
                        artworkCache = loadArtwork(state)
                        withContext(Dispatchers.Main) {
                            notificationArtwork = artworkCache.bitmap
                            player.updateState(state = state, artworkBytes = artworkCache.bytes)
                        }
                    }

                    else -> Unit
                }
            }
    }

    private fun buildMediaSession(player: HaRemoteMediaPlayer): MediaSession = MediaSession.Builder(context, player)
        .setId(id)
        .setCallback(MediaSessionCallback())
        .build()
        .also { session ->
            val tapIntent = LaunchActivity.newInstance(
                context = context,
                deepLink = LaunchActivity.DeepLink.NavigateTo(
                    FrontendTarget.EntityMoreInfo(config.entityId),
                    serverId = config.serverId,
                ),
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            session.sessionActivity = PendingIntent.getActivity(
                context,
                id.hashCode(),
                tapIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

    private suspend fun callMediaAction(action: String, extraData: Map<String, Any> = emptyMap()) {
        val actionData = buildMap {
            put("entity_id", config.entityId)
            putAll(extraData)
        }
        try {
            serverManager.integrationRepository(config.serverId)
                .callAction(MEDIA_PLAYER_DOMAIN, action, actionData)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Rethrow so the command Job fails: HaRemoteMediaPlayer fails the pending Media3
            // future on it, which drops the optimistic placeholder state and re-reads the real
            // one. Swallowing here would leave the future pending, since a failed action changes
            // nothing on the server and so produces no state update to complete it, and the shade
            // would keep showing the action as if it had succeeded. The command scope logs it.
            throw IllegalStateException("Failed to call media action $action on ${config.entityId}", e)
        }
    }

    /**
     * Resolves and loads artwork for [state], returning an [ArtworkCache] for the result.
     * Returns an empty [ArtworkCache] if the URL cannot be resolved or the load fails.
     */
    private suspend fun loadArtwork(state: EntityDisplayWithoutContext): ArtworkCache {
        val url = resolveArtworkUrl(state) ?: return ArtworkCache()
        val (bytes, bitmap) = loadArtworkData(url) ?: return ArtworkCache()
        return ArtworkCache(url = state.mediaPlayback?.entityPicturePath, bytes = bytes, bitmap = bitmap)
    }

    private suspend fun resolveArtworkUrl(state: EntityDisplayWithoutContext): String? {
        val entityPicturePath = state.mediaPlayback?.entityPicturePath ?: return null
        if (entityPicturePath.startsWith(HTTP_SCHEME_PREFIX)) return entityPicturePath

        val baseUrl = try {
            serverManager.connectionStateProvider(config.serverId)
                .urlFlow()
                .firstUrlOrNull()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to resolve artwork base URL for server ${config.serverId}")
            null
        } ?: return null

        return URL(baseUrl, entityPicturePath).toString()
    }

    /**
     * Loads album art bounded to [MAX_ARTWORK_SIZE] and returns JPEG-compressed bytes for media
     * metadata alongside a notification-icon-sized bitmap for [setLargeIcon][android.app.Notification.Builder.setLargeIcon].
     *
     * The bound matters because the source art is arbitrarily large: without it every configured
     * entity retains a native-resolution JPEG and bitmap for the lifetime of its session, and the
     * full resolution is compressed only to be thrown away for a notification-icon-sized bitmap.
     *
     * It also bounds what leaves the process. Media3 neither paginates nor resizes
     * [androidx.media3.common.MediaMetadata.artworkData]: it could throw `TransactionTooLargeException` if
     * the buffer is too big.
     *
     * Both outputs derive from that one bitmap, scaled with [scaleDownIfNecessary] here on IO
     * rather than during notification rendering.
     */
    private suspend fun loadArtworkData(url: String): Pair<ByteArray, Bitmap>? = withContext(Dispatchers.IO) {
        try {
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                // A bounding box rather than an output size: Scale.FIT keeps the aspect ratio, and
                // INEXACT stops art smaller than the box from being upscaled to fill it.
                .size(MAX_ARTWORK_SIZE, MAX_ARTWORK_SIZE)
                .precision(Precision.INEXACT)
                .build()
            val result = context.imageLoader.execute(request)
            result.image?.toBitmap()?.let { decodedBitmap ->
                // Coil only downsamples by powers of two, so the decoded bitmap can still be larger
                // than requested; bound it before compressing rather than trusting the request.
                val artworkBitmap = scaleDownIfNecessary(decodedBitmap, MAX_ARTWORK_SIZE, MAX_ARTWORK_SIZE)
                val stream = ByteArrayOutputStream()
                artworkBitmap.compress(CompressFormat.JPEG, ARTWORK_JPEG_QUALITY, stream)
                val maxIconSize = context.resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width)
                val notificationBitmap =
                    scaleDownIfNecessary(artworkBitmap, maxWidth = maxIconSize, maxHeight = maxIconSize)
                stream.toByteArray() to notificationBitmap
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

    /**
     * Mirrors AOSP's `Icon.scaleDownIfNecessary`: proportionally scales [bitmap] to fit within
     * [maxWidth] × [maxHeight], preserving aspect ratio. Returns [bitmap] unchanged if it already
     * fits. Run on IO to avoid the StrictMode CustomViolation triggered on API 36+ when the
     * framework calls the same method on the main thread during notification rendering.
     */
    private fun scaleDownIfNecessary(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        if (bitmap.width <= maxWidth && bitmap.height <= maxHeight) return bitmap
        val scale = minOf(maxWidth.toFloat() / bitmap.width, maxHeight.toFloat() / bitmap.height)
        return bitmap.scale(
            maxOf(1, (scale * bitmap.width).toInt()),
            maxOf(1, (scale * bitmap.height).toInt()),
        )
    }

    /** Immutable cache of the last successfully loaded artwork. */
    private data class ArtworkCache(val url: String? = null, val bytes: ByteArray? = null, val bitmap: Bitmap? = null)

    companion object {
        /** The `repeat` value Home Assistant uses for no repeat; the others come from [EntityExt]. */
        private const val MEDIA_PLAYER_REPEAT_OFF = "off"

        /** Artwork paths already carrying a scheme are absolute and need no base URL. */
        private const val HTTP_SCHEME_PREFIX = "http"

        /**
         * Largest edge, in pixels, of the album art sent as media metadata. Comfortably above every
         * surface that renders it (the notification icon is `notification_large_icon_width`, the
         * shade and lock screen a few hundred dp) while keeping the encoded bytes small enough to
         * hold per session and to send to out-of-process controllers.
         */
        private const val MAX_ARTWORK_SIZE = 512

        private const val ARTWORK_JPEG_QUALITY = 90

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
        fun create(config: MediaControlsEntityConfig): HaMediaSession
    }
}
