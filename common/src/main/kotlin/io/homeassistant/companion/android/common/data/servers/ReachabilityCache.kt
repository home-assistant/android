package io.homeassistant.companion.android.common.data.servers

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl

private val DEFAULT_TTL = 5.seconds

/**
 * Short-lived cache for URL reachability results.
 *
 * The connection state is read very often (every API call, widget update, WebSocket reconnect), so
 * without a cache each read would probe every configured URL again. Entries expire after [ttl] and
 * the whole cache can be dropped with [clear], for example when the network changed.
 *
 * Concurrent lookups of the same URL share a single probe: later callers wait for the running one and
 * reuse its result. A probe that was started before [clear] was called belongs to the previous network,
 * so its result is returned to its caller but never stored.
 */
internal class ReachabilityCache(
    private val ttl: Duration = DEFAULT_TTL,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private class Entry(val reachable: Boolean, val storedAt: TimeMark)

    private val entries = ConcurrentHashMap<HttpUrl, Entry>()
    private val locks = ConcurrentHashMap<HttpUrl, Mutex>()
    private val generation = AtomicInteger()

    /** Returns the cached result for [url] or runs [probe] and caches its result. */
    suspend fun getOrProbe(url: HttpUrl, probe: suspend (HttpUrl) -> Boolean): Boolean {
        freshResult(url)?.let { return it }
        return locks.getOrPut(url) { Mutex() }.withLock {
            // Another caller may have finished probing while this one was waiting for the lock.
            freshResult(url)?.let { return@withLock it }

            val generationBeforeProbe = generation.get()
            val reachable = probe(url)
            if (generationBeforeProbe == generation.get()) {
                entries[url] = Entry(reachable, timeSource.markNow())
            }
            reachable
        }
    }

    fun clear() {
        generation.incrementAndGet()
        entries.clear()
    }

    private fun freshResult(url: HttpUrl): Boolean? {
        return entries[url]?.takeIf { it.storedAt.elapsedNow() < ttl }?.reachable
    }
}
