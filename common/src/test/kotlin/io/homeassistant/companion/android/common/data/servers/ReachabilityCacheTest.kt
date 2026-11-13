package io.homeassistant.companion.android.common.data.servers

import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReachabilityCacheTest {
    private var now = 0L
    private var probes = 0
    private val cache = ReachabilityCache(ttlMs = 1_000, nowMs = { now })
    private val url = "https://a.example.com".toHttpUrl()

    private suspend fun probe(result: Boolean) = cache.getOrProbe(url) {
        probes++
        result
    }

    @Test
    fun `Given a fresh result when asking again then the probe is not repeated`() = runTest {
        assertTrue(probe(true))
        now = 999
        assertTrue(probe(false))

        assertEquals(1, probes)
    }

    @Test
    fun `Given an expired result when asking again then the URL is probed again`() = runTest {
        assertTrue(probe(true))
        now = 1_000
        assertFalse(probe(false))

        assertEquals(2, probes)
    }

    @Test
    fun `Given a failed probe when asking again in time then the failure is cached too`() = runTest {
        assertFalse(probe(false))
        assertFalse(probe(true))

        assertEquals(1, probes)
    }

    @Test
    fun `Given a cached result when cleared then the URL is probed again`() = runTest {
        assertTrue(probe(true))
        cache.clear()
        assertFalse(probe(false))

        assertEquals(2, probes)
    }

    @Test
    fun `Given different URLs when probing then each is cached separately`() = runTest {
        cache.getOrProbe(url) { true }
        val other = cache.getOrProbe("https://b.example.com".toHttpUrl()) { false }

        assertFalse(other)
    }
}
