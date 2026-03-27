package io.homeassistant.companion.android.mediacontrol

import android.content.Context
import android.graphics.Bitmap.CompressFormat
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.MEDIA_PLAYER_DOMAIN
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlEntityConfig
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlRepository
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlState
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.firstUrlOrNull
import io.homeassistant.companion.android.util.sensitive
import java.io.ByteArrayOutputStream
import java.net.URL
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Owns the [MediaSession] and [HaRemoteMediaPlayer] for a single Home Assistant media_player entity.
 *
 * Observes [MediaControlRepository] for entity state changes, loads artwork via Coil, and
 * translates Media3 player commands into Home Assistant service calls via [ServerManager].
 *
 * Call [release] when the session is no longer needed to cancel observation and release
 * Media3 resources.
 *
 * @param context Used for Coil image loading and [MediaSession] construction.
 * @param config Identifies the media_player entity this session represents.
 * @param mediaControlRepository Provides the per-entity state flow.
 * @param serverManager Used to resolve artwork base URLs and call HA integration actions.
 */
class HaMediaSession(
    private val context: Context,
    private val config: MediaControlEntityConfig,
    private val mediaControlRepository: MediaControlRepository,
    private val serverManager: ServerManager,
) {
    val mediaSession: MediaSession

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val player: HaRemoteMediaPlayer

    private var observationJob: Job? = null
    private var currentArtworkUrl: String? = null
    private var currentArtworkBytes: ByteArray? = null

    init {
        val commandCallback = object : HaRemoteMediaPlayer.CommandCallback {
            override fun onPlayRequested() {
                callMediaAction(ACTION_MEDIA_PLAY)
            }

            override fun onPauseRequested() {
                callMediaAction(ACTION_MEDIA_PAUSE)
            }

            override fun onSeekRequested(positionMs: Long) {
                callMediaAction(
                    action = ACTION_MEDIA_SEEK,
                    extraData = mapOf("seek_position" to positionMs / 1000.0),
                )
            }

            override fun onNextRequested() {
                callMediaAction(ACTION_MEDIA_NEXT_TRACK)
            }

            override fun onPreviousRequested() {
                callMediaAction(ACTION_MEDIA_PREVIOUS_TRACK)
            }

            override fun onSetVolumeRequested(volume: Float) {
                callMediaAction(
                    action = ACTION_VOLUME_SET,
                    extraData = mapOf("volume_level" to volume),
                )
            }

            override fun onIncreaseVolumeRequested() {
                callMediaAction(ACTION_VOLUME_UP)
            }

            override fun onDecreaseVolumeRequested() {
                callMediaAction(ACTION_VOLUME_DOWN)
            }
        }

        player = HaRemoteMediaPlayer(Looper.getMainLooper(), commandCallback)

        mediaSession = MediaSession.Builder(context, player)
            .setId("${config.serverId}_${config.entityId}")
            .setCallback(MediaSessionCallback())
            .build()
    }

    /**
     * Cancels any existing entity state observation and starts a new one for [config].
     */
    fun startObservingState() {
        observationJob?.cancel()
        currentArtworkUrl = null
        observationJob = scope.launch {
            mediaControlRepository.getEntityState(config)?.let { loadArtworkAndUpdatePlayer(it) }
            while (true) {
                ensureActive()
                mediaControlRepository.observeEntityState(config).collect { state ->
                    if (state == null) {
                        withContext(Dispatchers.Main) {
                            player.updateState(state = null, artworkPngBytes = null)
                        }
                        return@collect
                    }
                    loadArtworkAndUpdatePlayer(state)
                }
                // Flow completed (WebSocket not ready, connection lost, etc) — show buffering
                // state so the notification stays visible but controls are disabled, then retry
                withContext(Dispatchers.Main) { player.setConnecting() }
                Timber.d(
                    "Media control observation completed for ${config.entityId}, retrying in $OBSERVATION_RETRY_DELAY",
                )
                delay(OBSERVATION_RETRY_DELAY)
            }
        }
    }

    /** Releases Media3 resources and cancels all coroutines. */
    fun release() {
        scope.cancel()
        mediaSession.player.release()
        mediaSession.release()
    }

    private fun callMediaAction(action: String, extraData: Map<String, Any> = emptyMap()) {
        scope.launch {
            val actionData = hashMapOf<String, Any>("entity_id" to config.entityId)
            actionData.putAll(extraData)

            try {
                serverManager.integrationRepository(config.serverId)
                    .callAction(MEDIA_PLAYER_DOMAIN, action, actionData)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to call media action $action")
            }
        }
    }

    private suspend fun loadArtworkAndUpdatePlayer(state: MediaControlState) {
        val artworkUrl = resolveArtworkUrl(state)
        val pngBytes = if (artworkUrl != null && artworkUrl != currentArtworkUrl) {
            val bytes = loadBitmapAsPng(artworkUrl)
            if (bytes != null) {
                currentArtworkUrl = artworkUrl
                currentArtworkBytes = bytes
            }
            bytes ?: currentArtworkBytes
        } else if (artworkUrl == null) {
            currentArtworkUrl = null
            currentArtworkBytes
        } else {
            currentArtworkBytes
        }

        withContext(Dispatchers.Main) {
            player.updateState(state = state, artworkPngBytes = pngBytes)
        }
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
        } catch (e: IllegalStateException) {
            Timber.e(e, "Server does not exist for artwork URL resolution")
            null
        } ?: return null

        return URL(baseUrl, entityPictureUrl).toString()
    }

    /** Loads album art and compresses to PNG bytes on the IO dispatcher. */
    private suspend fun loadBitmapAsPng(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(ARTWORK_SIZE_PX)
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

    private companion object {
        val OBSERVATION_RETRY_DELAY = 5.seconds
        const val ARTWORK_SIZE_PX = 512

        const val ACTION_MEDIA_PLAY = "media_play"
        const val ACTION_MEDIA_PAUSE = "media_pause"
        const val ACTION_MEDIA_SEEK = "media_seek"
        const val ACTION_MEDIA_NEXT_TRACK = "media_next_track"
        const val ACTION_MEDIA_PREVIOUS_TRACK = "media_previous_track"
        const val ACTION_VOLUME_SET = "volume_set"
        const val ACTION_VOLUME_UP = "volume_up"
        const val ACTION_VOLUME_DOWN = "volume_down"
    }
}
