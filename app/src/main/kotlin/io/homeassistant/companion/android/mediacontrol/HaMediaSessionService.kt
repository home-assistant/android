package io.homeassistant.companion.android.mediacontrol

import android.content.Context
import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlEntityConfig
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    lateinit var haMediaSessionFactory: HaMediaSession.Factory

    // Keyed by "$serverId:$entityId". Each entry pairs the session with the job running observe().
    internal val activeSessions = mutableMapOf<String, Pair<HaMediaSession, Job>>()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this).build()
        notificationProvider.setSmallIcon(commonR.drawable.ic_stat_ic_notification)
        setMediaNotificationProvider(notificationProvider)
        Timber.d("HaMediaSessionService created")
        startObservingEntities()
    }

    /**
     * Starts collecting [MediaControlRepository.observeConfiguredEntities] and reconciling
     * sessions on each emission.
     *
     * Both parameters are extracted to allow tests to supply controlled scopes without
     * calling [onCreate], which would trigger Hilt injection and Media3 setup.
     *
     * @param scope The scope used to collect the entities flow.
     * @param sessionScope The scope used to launch each session's [HaMediaSession.observe] coroutine.
     */
    internal fun startObservingEntities(
        scope: CoroutineScope = serviceScope,
        sessionScope: CoroutineScope = serviceScope,
    ) {
        mediaControlRepository.observeConfiguredEntities()
            .onEach { entities -> withContext(Dispatchers.Main) { reconcileSessions(entities, sessionScope) } }
            .launchIn(scope)
    }

    // Returns null intentionally: Media3 routes each controller to the session whose ID matches
    // the one it was constructed with. Returning a specific session here would cause all
    // controllers (including the notification) to connect to that one session, breaking
    // multi-session behavior where each entity has its own independent media control card.
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("HaMediaSessionService onStartCommand, ${activeSessions.size} active sessions")
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val anyPlaying = activeSessions.values.any { (session, _) ->
            session.mediaSession?.player?.let { it.playWhenReady && it.mediaItemCount > 0 } == true
        }
        if (!anyPlaying) stopSelf()
    }

    override fun onDestroy() {
        Timber.d("HaMediaSessionService destroyed")
        activeSessions.values.forEach { (session, job) ->
            session.mediaSession?.let { removeSession(it) }
            job.cancel()
        }
        activeSessions.clear()
        serviceScope.cancel()
        super.onDestroy()
    }

    internal fun reconcileSessions(
        configuredEntities: List<MediaControlEntityConfig>,
        sessionScope: CoroutineScope = serviceScope,
    ) {
        if (configuredEntities.isEmpty()) {
            Timber.d("No media control entities configured, stopping service")
            stopSelf()
            return
        }

        val desiredKeys = configuredEntities.map { it.sessionKey() }.toSet()
        val currentKeys = activeSessions.keys.toSet()

        // Remove sessions that are no longer configured
        (currentKeys - desiredKeys).forEach { key ->
            val (session, job) = activeSessions.remove(key) ?: return@forEach
            session.mediaSession?.let { removeSession(it) }
            job.cancel()
            Timber.d("Removed media session for $key")
        }

        // Add sessions for newly configured entities
        (desiredKeys - currentKeys).forEach { key ->
            val entityConfig = configuredEntities.first { it.sessionKey() == key }
            val session = haMediaSessionFactory.create(
                context = this@HaMediaSessionService,
                config = entityConfig,
            )
            val job = sessionScope.launch {
                session.observe { mediaSession ->
                    withContext(Dispatchers.Main) { addSession(mediaSession) }
                }
            }
            activeSessions[key] = session to job
            Timber.d("Added media session for $key")
        }
    }

    companion object {
        /**
         * Starts the service if any media_player entities are configured.
         * Should be called from a foreground context (e.g. Activity) to avoid
         * Android 15+ restrictions on starting mediaPlayback foreground services from background.
         * Once running, the service observes the database and reconciles sessions automatically.
         */
        suspend fun startIfConfigured(context: Context, mediaControlRepository: MediaControlRepository) {
            if (mediaControlRepository.getConfiguredEntities().isNotEmpty()) {
                Timber.d("Media control entities configured, starting HaMediaSessionService")
                try {
                    context.startService(Intent(context, HaMediaSessionService::class.java))
                } catch (e: Exception) {
                    Timber.e(e, "Failed to start HaMediaSessionService")
                }
            }
        }
    }
}

private fun MediaControlEntityConfig.sessionKey(): String = "$serverId:$entityId"
