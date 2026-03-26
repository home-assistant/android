package io.homeassistant.companion.android.mediacontrol

import android.content.Context
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlEntityConfig
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * A [MediaSessionService] that exposes one or more Home Assistant media_player entities as native
 * Android media controls in the notification shade. Each configured entity gets its own
 * [HaMediaSession] and [MediaSession], which Media3 registers and presents individually.
 *
 * This service is responsible only for the Android service lifecycle and session reconciliation.
 * All per-entity session logic is delegated to [HaMediaSession].
 */
@AndroidEntryPoint
class HaMediaSessionService : MediaSessionService() {

    @Inject
    lateinit var mediaControlRepository: MediaControlRepository

    @Inject
    lateinit var serverManager: ServerManager

    // Keyed by "$serverId:$entityId"
    private val activeSessions = mutableMapOf<String, HaMediaSession>()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        Timber.d("HaMediaSessionService created")
        reconcileSessions()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_RESTART_OBSERVATION) {
            Timber.d("Restarting observation due to config change")
            reconcileSessions()
        }
        return result
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        activeSessions.values.firstOrNull()?.mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val anyPlaying = activeSessions.values.any { session ->
            session.mediaSession.player.playWhenReady && session.mediaSession.player.mediaItemCount > 0
        }
        if (!anyPlaying) stopSelf()
    }

    override fun onDestroy() {
        Timber.d("HaMediaSessionService destroyed")
        activeSessions.values.forEach { it.release() }
        activeSessions.clear()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun reconcileSessions() {
        serviceScope.launch {
            val configuredEntities = mediaControlRepository.getConfiguredEntities()
            if (configuredEntities.isEmpty()) {
                Timber.d("No media control entities configured, stopping service")
                stopSelf()
                return@launch
            }

            val desiredKeys = configuredEntities.map { it.sessionKey() }.toSet()
            val currentKeys = activeSessions.keys.toSet()

            // Remove sessions that are no longer configured
            (currentKeys - desiredKeys).forEach { key ->
                val session = activeSessions.remove(key) ?: return@forEach
                removeSession(session.mediaSession)
                session.release()
                Timber.d("Removed media session for $key")
            }

            // Add sessions for newly configured entities
            (desiredKeys - currentKeys).forEach { key ->
                val entityConfig = configuredEntities.first { it.sessionKey() == key }
                val session = HaMediaSession(
                    context = this@HaMediaSessionService,
                    config = entityConfig,
                    mediaControlRepository = mediaControlRepository,
                    serverManager = serverManager,
                )
                addSession(session.mediaSession)
                session.startObservingState()
                activeSessions[key] = session
                Timber.d("Added media session for $key")
            }
        }
    }

    internal companion object {
        const val ACTION_RESTART_OBSERVATION =
            "io.homeassistant.companion.android.mediacontrol.RESTART_OBSERVATION"

        /**
         * Starts the service if any media_player entities are configured.
         * Should be called from a foreground context (e.g. Activity) to avoid
         * Android 15+ restrictions on starting mediaPlayback foreground services from background.
         */
        suspend fun startIfConfigured(context: Context, mediaControlRepository: MediaControlRepository) {
            if (mediaControlRepository.getConfiguredEntities().isNotEmpty()) {
                Timber.d("Media control entities configured, starting HaMediaSessionService")
                context.startService(Intent(context, HaMediaSessionService::class.java))
            }
        }

        /**
         * Should be called after a server is removed. The service will re-check its configuration,
         * remove any sessions for the deleted server, and stop itself if none remain.
         */
        fun onServerRemoved(context: Context) {
            context.startService(
                Intent(context, HaMediaSessionService::class.java)
                    .setAction(ACTION_RESTART_OBSERVATION),
            )
        }
    }
}

private fun MediaControlEntityConfig.sessionKey(): String = "$serverId:$entityId"
