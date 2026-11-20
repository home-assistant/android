package io.homeassistant.companion.android.common.data.servers

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReachabilityCacheTest {
    private val timeSource = TestTimeSource()
    private var probes = 0
    private val cache = ReachabilityCache(ttl = 1.seconds, timeSource = timeSource)
    private val url = "https://a.example.com".toHttpUrl()

    private suspend fun probe(result: Boolean) = cache.getOrProbe(url) {
        probes++
        result
    }

    @Test
    fun `Given a fresh result when asking again then the probe is not repeated`() = runTest {
        assertTrue(probe(true))
        timeSource += 999.milliseconds
        assertTrue(probe(false))

        assertEquals(1, probes)
    }

    @Test
    fun `Given an expired result when asking again then the URL is probed again`() = runTest {
        assertTrue(probe(true))
        timeSource += 1.seconds
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

    @Test
    fun `Given a probe in progress when the same URL is requested then only one probe runs`() = runTest {
        val release = CompletableDeferred<Unit>()
        val first = async {
            cache.getOrProbe(url) {
                probes++
                release.await()
                true
            }
        }
        val second = async { cache.getOrProbe(url) { probes++.let { false } } }
        advanceUntilIdle()

        release.complete(Unit)

        assertTrue(first.await())
        assertTrue(second.await())
        assertEquals(1, probes)
    }

    @Test
    fun `Given a probe that started before clear when it finishes then its result is not stored`() = runTest {
        val release = CompletableDeferred<Unit>()
        val stale = async {
            cache.getOrProbe(url) {
                probes++
                release.await()
                true
            }
        }
        advanceUntilIdle()

        cache.clear()
        release.complete(Unit)
        assertTrue(stale.await())

        assertFalse(probe(false))
        assertEquals(2, probes)
    }
}
