package io.homeassistant.companion.android.common.data.servers

import java.util.concurrent.ConcurrentHashMap
import okhttp3.HttpUrl

/**
 * Short-lived cache for URL reachability results.
 *
 * The connection state is read very often (every API call, widget update, WebSocket reconnect), so
 * without a cache each read would probe every configured URL again. Entries expire after [ttlMs] and
 * the whole cache can be dropped with [clear], for example when the network changed.
 */
internal class ReachabilityCache(
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val nowMs: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
) {
    private class Entry(val reachable: Boolean, val atMs: Long)

    private val entries = ConcurrentHashMap<HttpUrl, Entry>()

    /** Returns the cached result for [url] or runs [probe] and caches its result. */
    suspend fun getOrProbe(url: HttpUrl, probe: suspend (HttpUrl) -> Boolean): Boolean {
        val cached = entries[url]
        if (cached != null && nowMs() - cached.atMs < ttlMs) return cached.reachable
        val reachable = probe(url)
        entries[url] = Entry(reachable, nowMs())
        return reachable
    }

    fun clear() = entries.clear()

    companion object {
        const val DEFAULT_TTL_MS = 5_000L
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}
