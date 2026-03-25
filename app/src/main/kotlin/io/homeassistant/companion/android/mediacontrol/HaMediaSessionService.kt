package io.homeassistant.companion.android.mediacontrol

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap.CompressFormat
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.toBitmap
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.MEDIA_PLAYER_DOMAIN
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlRepository
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlState
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.firstUrlOrNull
import io.homeassistant.companion.android.util.sensitive
import java.io.ByteArrayOutputStream
import javax.inject.Inject
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
 * A [MediaSessionService] that exposes a Home Assistant media_player entity as a native
 * Android media control in the notification shade.
 *
 * It creates a [HaRemoteMediaPlayer] that reports HA entity state and translates user
 * interactions (play/pause/seek/next/previous) into HA service calls.
 */
@AndroidEntryPoint
class HaMediaSessionService : MediaSessionService() {

    @Inject
    lateinit var mediaControlRepository: MediaControlRepository

    @Inject
    lateinit var serverManager: ServerManager

    private var mediaSession: MediaSession? = null
    private var player: HaRemoteMediaPlayer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var observationJob: Job? = null
    private var currentArtworkUrl: String? = null
    private var currentArtworkBytes: ByteArray? = null

    override fun onCreate() {
        super.onCreate()
        Timber.d("HaMediaSessionService created")

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
        }

        val newPlayer = HaRemoteMediaPlayer(Looper.getMainLooper(), commandCallback)
        player = newPlayer

        val session = MediaSession.Builder(this, newPlayer)
            .setCallback(MediaSessionCallback())
            .build()
        mediaSession = session
        addSession(session)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        val configChanged = intent?.action == ACTION_RESTART_OBSERVATION
        if (configChanged || observationJob?.isActive != true) {
            Timber.d("Starting observation (configChanged=%s, jobActive=%s)", configChanged, observationJob?.isActive)
            startObservingState()
        }
        return result
    }

    /**
     * Cancels any existing entity state observation and starts a new one,
     * re-reading the current configuration from preferences.
     */
    private fun startObservingState() {
        observationJob?.cancel()
        currentArtworkUrl = null
        currentArtworkBytes = null
        val currentPlayer = player ?: return
        observationJob = serviceScope.launch(Dispatchers.IO) {
            while (true) {
                ensureActive()
                val isConfigured = mediaControlRepository.getConfiguredServerId() != null &&
                    mediaControlRepository.getConfiguredEntityId() != null
                if (!isConfigured) {
                    Timber.d("No media control entity configured, stopping service")
                    withContext(Dispatchers.Main) { stopSelf() }
                    return@launch
                }
                mediaControlRepository.observeMediaControlState().collect { state ->
                    if (state == null) {
                        withContext(Dispatchers.Main) {
                            currentPlayer.updateState(state = null, artworkPngBytes = null)
                        }
                        return@collect
                    }
                    loadArtworkAndUpdatePlayer(state)
                }
                // Flow completed (WebSocket not ready, connection lost, etc) — retry
                Timber.d("Media control observation completed, retrying in %s", OBSERVATION_RETRY_DELAY)
                delay(OBSERVATION_RETRY_DELAY)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val currentPlayer = mediaSession?.player
        if (currentPlayer == null || !currentPlayer.playWhenReady || currentPlayer.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        Timber.d("HaMediaSessionService destroyed")
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        super.onDestroy()
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
                    sensitive(controller.packageName),
                )
                return MediaSession.ConnectionResult.reject()
            }
            return MediaSession.ConnectionResult.accept(
                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS,
                MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS,
            )
        }
    }

    private fun callMediaAction(action: String, extraData: Map<String, Any> = emptyMap()) {
        serviceScope.launch(Dispatchers.IO) {
            val serverId = mediaControlRepository.getConfiguredServerId()
            val entityId = mediaControlRepository.getConfiguredEntityId()
            if (serverId == null || entityId == null) {
                Timber.w("Cannot call media action %s: no configured entity", action)
                return@launch
            }

            val actionData = hashMapOf<String, Any>("entity_id" to entityId)
            actionData.putAll(extraData)

            try {
                serverManager.integrationRepository(serverId)
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
            currentArtworkBytes = null
            null
        } else {
            currentArtworkBytes
        }

        withContext(Dispatchers.Main) {
            player?.updateState(state = state, artworkPngBytes = pngBytes)
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

    internal companion object {
        const val ACTION_RESTART_OBSERVATION =
            "io.homeassistant.companion.android.mediacontrol.RESTART_OBSERVATION"
        val OBSERVATION_RETRY_DELAY = 5.seconds
        private const val ARTWORK_SIZE_PX = 512

        /**
         * Starts the service if a media_player entity is configured.
         * Should be called from a foreground context (e.g. Activity) to avoid
         * Android 15+ restrictions on starting mediaPlayback foreground services from background.
         */
        suspend fun startIfConfigured(context: Context, mediaControlRepository: MediaControlRepository) {
            val serverId = mediaControlRepository.getConfiguredServerId()
            val entityId = mediaControlRepository.getConfiguredEntityId()
            if (serverId != null && entityId != null) {
                Timber.d("Media control entity configured, starting HaMediaSessionService")
                context.startService(Intent(context, HaMediaSessionService::class.java))
            }
        }
    }

    /** Loads album art and compresses to PNG bytes on the IO dispatcher. */
    private suspend fun loadBitmapAsPng(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val request = ImageRequest.Builder(this@HaMediaSessionService)
                .data(url)
                .size(ARTWORK_SIZE_PX)
                .build()
            val result = imageLoader.execute(request)
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
}
