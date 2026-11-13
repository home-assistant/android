package io.homeassistant.companion.android.sensors

import android.location.Location
import io.homeassistant.companion.android.sensors.LocationSensorManager.LocationUpdateTrigger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A location fix waiting to be sent to a server.
 *
 * @property bypassDebounce true when this fix, or a fix it replaced in the queue, came from a geofence transition and
 * must therefore not be dropped by the send debounce.
 */
internal data class QueuedLocation(
    val location: Location,
    val trigger: LocationUpdateTrigger?,
    val bypassDebounce: Boolean = trigger?.isGeofence == true,
)

/**
 * Sends location fixes to each server one at a time, keeping only the newest fix that is waiting behind an in-flight
 * send so that a backlog collapses into a single send of the latest position and an older fix can never land after a
 * newer one.
 *
 * Servers are independent: a slow or unreachable server never delays sends to another one.
 *
 * @param send performs the actual send for a server; it is never invoked concurrently for the same server.
 * @param onSuperseded called for every fix that was dropped because a newer one arrived before it could be sent.
 */
internal class LocationSendQueue(
    private val send: suspend (serverId: Int, queued: QueuedLocation) -> Unit,
    private val onSuperseded: suspend (serverId: Int, superseded: QueuedLocation) -> Unit,
) {
    private val stateMutex = Mutex()
    private val pending = mutableMapOf<Int, QueuedLocation>()
    private val sendMutexes = mutableMapOf<Int, Mutex>()

    /**
     * Queues [queued] for [serverId] and sends it once the server's in-flight send, if any, finishes, unless a newer
     * fix arrives first. Returns after the fix was sent, or right away when it was superseded.
     */
    suspend fun submit(serverId: Int, queued: QueuedLocation) {
        val offer = stateMutex.withLock { offer(serverId, queued) }
        offer.superseded?.let { onSuperseded(serverId, it) }
        if (!offer.accepted) return

        offer.sendMutex.withLock {
            // Null when an earlier waiter already took the newest fix for this server
            val next = stateMutex.withLock { pending.remove(serverId) } ?: return
            send(serverId, next)
        }
    }

    private class Offer(val sendMutex: Mutex, val accepted: Boolean, val superseded: QueuedLocation?)

    /** Must be called with [stateMutex] held. */
    private fun offer(serverId: Int, candidate: QueuedLocation): Offer {
        val current = pending[serverId]
        val accepted = current == null || current.location.time < candidate.location.time
        val winner = if (accepted) candidate else current
        val superseded = if (accepted) current else candidate
        pending[serverId] = winner.copy(bypassDebounce = winner.bypassDebounce || superseded?.bypassDebounce == true)
        return Offer(sendMutexes.getOrPut(serverId) { Mutex() }, accepted, superseded)
    }
}
