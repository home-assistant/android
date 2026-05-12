package io.homeassistant.companion.android.mediacontrol

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.util.UnstableApi
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
import kotlinx.coroutines.cancelAndJoin
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
 * Notifications are managed via [onUpdateNotification], which is called per-session by Media3
 * whenever a session's player state changes. Each entity receives a notification with a unique ID
 * derived from its session ID, so each entity appears as a separate card in the notification shade.
 *
 * This service is responsible only for the Android service lifecycle and session reconciliation.
 * All per-entity session logic is delegated to [HaMediaSession].
 */
@AndroidEntryPoint
class HaMediaSessionService @VisibleForTesting constructor(private val serviceScope: CoroutineScope) :
    MediaSessionService() {

    @Inject constructor() : this(CoroutineScope(SupervisorJob() + Dispatchers.Default))

    @Inject
    lateinit var mediaControlRepository: MediaControlRepository

    @Inject
    lateinit var haMediaSessionFactory: HaMediaSession.Factory

    // Keyed by "$serverId:$entityId". Each entry pairs the session with the job running observe().
    private val activeSessions = mutableMapOf<String, Pair<HaMediaSession, Job>>()

    /** The notification ID last passed to [startForeground], or null if not in the foreground. */
    private var foregroundNotificationId: Int? = null
    private val notificationManager by lazy { NotificationManagerCompat.from(this) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Timber.d("HaMediaSessionService created")
        startObservingEntities()
    }

    // Returns null intentionally: Media3 routes each controller to the session whose ID matches
    // the one it was constructed with. Returning a specific session here would cause all
    // controllers (including the notification) to connect to that one session, breaking
    // multi-session behavior where each entity has its own independent media control card.
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        val anyPlaying = activeSessions.values.any { (session, _) -> session.isPlaying }
        // Keep the service alive while playback is active so the media notification remains
        // visible and controllable from the notification shade after the app is dismissed.
        // If nothing is playing there is no reason to keep the service alive.
        // Note: there is no automatic stop when playback ends after this point — the service
        // will only stop when the user removes all configured entities, which causes
        // reconcileSessions to call stopSelf() on an empty list.
        if (!anyPlaying) {
            stopSelf()
        }
    }

    /**
     * Called by Media3 whenever a session's player state changes and the notification needs to be
     * updated. Each session gets a notification with a unique ID derived from the session's ID,
     * so each entity appears as its own card in the media controls carousel.
     */
    // POST_NOTIFICATIONS is not required for notifications linked to an active MediaSession
    // (MediaStyle notifications). This is a platform-level guarantee on API 33+; on API < 33
    // the permission does not exist at all.
    @SuppressLint("MissingPermission")
    @OptIn(UnstableApi::class)
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        val notificationId = session.id.hashCode()

        // A session not in activeSessions is being torn down. removeSession() and player.release()
        // both trigger onUpdateNotification, so without this guard we would re-post a notification
        // we just cancelled, leaving a zombie media control card after removal.
        val haSession = activeSessions.values.firstOrNull { (haSession, _) -> haSession.id == session.id }?.first

        if (haSession == null || session.player.mediaItemCount == 0) {
            // Entity is off, no state has arrived yet, or the session is being torn down.
            notificationManager.cancel(notificationId)
            if (foregroundNotificationId == notificationId) {
                promoteForegroundOrStop(excludeKey = session.id)
            }
            return
        }

        val notification = haSession.buildNotification() ?: return
        if (foregroundNotificationId == null && startInForegroundRequired) {
            // Service is not yet in the foreground and playback requires it — start foreground
            // with this session's notification. All subsequent sessions (and updates to this one)
            // go through notificationManager.notify() to avoid replacing the foreground
            // notification ID, which would dismiss the previously-shown notification on Android 13+.
            startForeground(notificationId, notification)
            foregroundNotificationId = notificationId
        } else {
            // Service is already in the foreground (or foreground not yet required).
            // notificationManager.notify() works for both regular notifications and for updating
            // the foreground notification in-place when the ID matches.
            notificationManager.notify(notificationId, notification)
        }
    }

    override fun onDestroy() {
        Timber.d("HaMediaSessionService destroyed")
        if (foregroundNotificationId != null) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            foregroundNotificationId = null
        }
        // Snapshot and clear activeSessions before calling removeSession so that the
        // onUpdateNotification guard (!isActive check) treats these sessions as inactive and
        // cancels rather than re-posts their notifications during teardown.
        val sessionsToClean = activeSessions.values.toList()
        activeSessions.clear()
        sessionsToClean.forEach { (session, job) ->
            notificationManager.cancel(session.id.hashCode())
            session.mediaSession?.let { removeSession(it) }
            job.cancel()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startObservingEntities() {
        mediaControlRepository.observeConfiguredEntities()
            .onEach { entities -> reconcileSessions(entities) }
            .launchIn(serviceScope)
    }

    private suspend fun reconcileSessions(configuredEntities: List<MediaControlEntityConfig>) {
        val desiredKeys = configuredEntities.map { it.sessionKey() }.toSet()
        Timber.d("reconcileSessions: received ${configuredEntities.size} entities=$desiredKeys")

        if (configuredEntities.isEmpty()) {
            Timber.d("No media control entities configured, stopping service")
            withContext(Dispatchers.Main) { stopSelf() }
            return
        }

        // All activeSessions reads and writes are confined to the Main thread to avoid data races
        // with onUpdateNotification and onTaskRemoved, which are always called on Main by the OS
        // and Media3. The diff is computed here on Main as well since it operates on small sets.
        withContext(Dispatchers.Main) {
            val currentKeys = activeSessions.keys.toSet()

            Timber.d("reconcileSessions: desiredKeys=$desiredKeys, currentKeys=$currentKeys")

            val toRemove = (currentKeys - desiredKeys).mapNotNull { key ->
                val pair = activeSessions.remove(key) ?: return@mapNotNull null
                key to pair
            }
            val toAdd = (desiredKeys - currentKeys).map { key ->
                val entityConfig = configuredEntities.first { it.sessionKey() == key }
                key to haMediaSessionFactory.create(config = entityConfig)
            }

            Timber.d("reconcileSessions: toRemove=${toRemove.map { it.first }}, toAdd=${toAdd.map { it.first }}")

            toRemove.forEach { (key, pair) -> tearDownSession(key, pair) }
            toAdd.forEach { (key, session) -> launchSession(key, session) }

            Timber.d("reconcileSessions: done, activeSessions=${activeSessions.keys.toList()}")
        }
    }

    /**
     * Cancels the notification for a session, unregisters it from the service, and joins the
     * observation coroutine so all Media3 resources are released before returning.
     * Must be called from the Main dispatcher.
     */
    private suspend fun tearDownSession(key: String, pair: Pair<HaMediaSession, Job>) {
        val (haSession, job) = pair
        val notificationId = key.hashCode()
        notificationManager.cancel(notificationId)
        if (foregroundNotificationId == notificationId) {
            promoteForegroundOrStop(excludeKey = key)
        }
        haSession.mediaSession?.let { removeSession(it) }
        job.cancelAndJoin()
        Timber.d("Removed media session for $key")
    }

    /**
     * Launches the observation coroutine for a new session, registers it with the service, and
     * stores it in [activeSessions].
     * Must be called from the Main thread to safely mutate [activeSessions].
     */
    private fun launchSession(key: String, session: HaMediaSession) {
        val job = serviceScope.launch {
            session.observe { mediaSession -> addSession(mediaSession) }
        }
        activeSessions[key] = session to job
        Timber.d("Added media session for $key")
    }

    /**
     * Promotes a remaining active session to the foreground notification when the current
     * foreground session is removed or goes idle. If no active session has media content,
     * stops the foreground state.
     *
     * @param excludeKey The map key of the session being removed, to skip it when searching
     * for a replacement.
     */
    private fun promoteForegroundOrStop(excludeKey: String) {
        val nextSession = activeSessions.entries
            .firstOrNull { (key, pair) -> key != excludeKey && pair.first.hasActiveMedia }
            ?.value?.first

        if (nextSession != null) {
            val nextId = nextSession.id.hashCode()
            val notification = nextSession.buildNotification() ?: return
            startForeground(nextId, notification)
            foregroundNotificationId = nextId
            Timber.d("promoteForegroundOrStop: promoted session ${nextSession.id}")
        } else {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            foregroundNotificationId = null
            Timber.d("promoteForegroundOrStop: no active sessions, stopped foreground")
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            HaMediaSession.NOTIFICATION_CHANNEL_ID,
            getString(commonR.string.media_controls),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        /**
         * Starts the service. Should be called from a foreground context (e.g. Activity) to avoid
         * Android 15+ restrictions on starting mediaPlayback foreground services from background.
         * If no entities are configured the service will stop itself immediately after starting.
         * Once running, the service observes the database and reconciles sessions automatically.
         */
        fun start(context: Context) {
            Timber.d("Starting HaMediaSessionService")
            try {
                context.startService(Intent(context, HaMediaSessionService::class.java))
            } catch (e: Exception) {
                Timber.e(e, "Failed to start HaMediaSessionService")
            }
        }
    }
}

private fun MediaControlEntityConfig.sessionKey(): String = "$serverId:$entityId"
