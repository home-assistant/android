package io.homeassistant.companion.android.mediacontrol

import android.content.Context
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import javax.inject.Inject
import timber.log.Timber

/**
 * A [MediaSessionService] that exposes a Home Assistant media_player entity as a native
 * Android media control in the notification shade.
 *
 * This service is responsible only for the Android service lifecycle. All session logic —
 * state observation, artwork loading, and HA service calls — is delegated to [HaMediaSession].
 */
@AndroidEntryPoint
class HaMediaSessionService : MediaSessionService() {

    @Inject
    lateinit var mediaControlRepository: MediaControlRepository

    @Inject
    lateinit var serverManager: ServerManager

    private var haMediaSession: HaMediaSession? = null

    override fun onCreate() {
        super.onCreate()
        Timber.d("HaMediaSessionService created")
        haMediaSession = HaMediaSession(
            context = this,
            mediaControlRepository = mediaControlRepository,
            serverManager = serverManager,
            onStopRequested = { stopSelf() },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        val configChanged = intent?.action == ACTION_RESTART_OBSERVATION
        val session = haMediaSession ?: return result
        if (configChanged || !session.isObserving) {
            Timber.d("Starting observation (configChanged=%s, isObserving=%s)", configChanged, session.isObserving)
            session.startObservingState()
        }
        return result
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        haMediaSession?.mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val currentPlayer = haMediaSession?.mediaSession?.player
        if (currentPlayer == null || !currentPlayer.playWhenReady || currentPlayer.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        Timber.d("HaMediaSessionService destroyed")
        haMediaSession?.release()
        haMediaSession = null
        super.onDestroy()
    }

    internal companion object {
        const val ACTION_RESTART_OBSERVATION =
            "io.homeassistant.companion.android.mediacontrol.RESTART_OBSERVATION"

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

        /**
         * Should be called after a server is removed. If the service is running with the removed
         * server's configuration, it will re-check its config, find no entity, and stop itself.
         */
        fun onServerRemoved(context: Context) {
            context.startService(
                Intent(context, HaMediaSessionService::class.java)
                    .setAction(ACTION_RESTART_OBSERVATION),
            )
        }
    }
}
