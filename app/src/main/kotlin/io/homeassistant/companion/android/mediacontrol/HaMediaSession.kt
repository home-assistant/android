package io.homeassistant.companion.android.mediacontrol

import android.content.Context
import android.graphics.Bitmap.CompressFormat
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.toBitmap
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.MEDIA_PLAYER_DOMAIN
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlEntityConfig
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlRepository
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlState
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.firstUrlOrNull
import io.homeassistant.companion.android.util.sensitive
import java.io.ByteArrayOutputStream
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
 * @param onEntityGone Called on the main thread when the WebSocket flow ends unexpectedly and
 *   the retry loop gives up, so the hosting service can remove this session.
 */
class HaMediaSession(
    private val context: Context,
    private val config: MediaControlEntityConfig,
    private val mediaControlRepository: MediaControlRepository,
    private val serverManager: ServerManager,
    private val onEntityGone: () -> Unit,
) {
    val mediaSession: MediaSession

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val player: HaRemoteMediaPlayer

    private var observationJob: Job? = null
    private var currentArtworkUrl: String? = null
    private var currentArtworkBytes: ByteArray? = null

    /** Whether a state observation coroutine is currently active. */
    val isObserving: Boolean get() = observationJob?.isActive == true

    init {
        val commandCallback = object : HaRemoteMediaPlayer.CommandCallback {
            override fun onPlayRequested() {
                callMediaAction("media_play")
            }

            override fun onPauseRequested() {
                callMediaAction("media_pause")
            }

            override fun onSeekRequested(positionMs: Long) {
                callMediaAction(
                    action = "media_seek",
                    extraData = mapOf("seek_position" to positionMs / 1000.0),
                )
            }

            override fun onNextRequested() {
                callMediaAction("media_next_track")
            }

            override fun onPreviousRequested() {
                callMediaAction("media_previous_track")
            }

            override fun onSetVolumeRequested(volume: Float) {
                callMediaAction(
                    action = "volume_set",
                    extraData = mapOf("volume_level" to volume),
                )
            }

            override fun onIncreaseVolumeRequested() {
                callMediaAction("volume_up")
            }

            override fun onDecreaseVolumeRequested() {
                callMediaAction("volume_down")
            }
        }

        player = HaRemoteMediaPlayer(Looper.getMainLooper(), commandCallback)

        mediaSession = MediaSession.Builder(context, player)
            .setCallback(MediaSessionCallback())
            .build()
    }

    /**
     * Cancels any existing entity state observation and starts a new one for [config].
     */
    fun startObservingState() {
        observationJob?.cancel()
        currentArtworkUrl = null
        currentArtworkBytes = null
        observationJob = scope.launch(Dispatchers.IO) {
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
                // Flow completed (WebSocket not ready, connection lost, etc) — retry
                Timber.d(
                    "Media control observation completed for %s, retrying in %s",
                    config.entityId,
                    OBSERVATION_RETRY_DELAY,
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
        scope.launch(Dispatchers.IO) {
            val actionData = hashMapOf<String, Any>("entity_id" to config.entityId)
            actionData.putAll(extraData)

            try {
                serverManager.integrationRepository(config.serverId)
                    .callAction(MEDIA_PLAYER_DOMAIN, action, actionData)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to call media action %s", action)
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
            currentArtworkBytes = null
            null
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
                ?.toString()
                ?.removeSuffix("/")
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            Timber.e(e, "Server does not exist for artwork URL resolution")
            null
        } ?: return null

        return "$baseUrl$entityPictureUrl"
    }

    /** Loads album art and compresses to PNG bytes on the IO dispatcher. */
    private suspend fun loadBitmapAsPng(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(ARTWORK_SIZE_PX)
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
            Timber.e(e, "Failed to load album art from %s", sensitive(url))
            null
        }
    }

    /**
     * Restricts media session connections to trusted controllers (same app, system,
     * or apps with MEDIA_CONTENT_CONTROL / notification listener access).
     */
    @OptIn(UnstableApi::class)
    private inner class MediaSessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            if (!controller.isTrusted) {
                Timber.w(
                    "Rejecting connection from untrusted media controller package=%s",
                    controller.packageName,
                )
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
    }
}
