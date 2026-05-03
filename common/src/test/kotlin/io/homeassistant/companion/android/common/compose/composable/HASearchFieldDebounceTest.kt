package io.homeassistant.companion.android.common.compose.composable

import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests for [debouncedSearchUpdate], the timing helper extracted from HASearchField.
 *
 * Because the helper is a plain suspend function (not a Composable) we can drive it directly with
 * the [runTest] virtual time machine to verify debounce, instant-clear, and cancellation rules
 * without standing up a Compose host.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HASearchFieldDebounceTest {

    private val debounce = 300.milliseconds

    @Test
    fun `Given empty query when debouncing then emit immediately without delay`() = runTest {
        val emitted = mutableListOf<String>()

        debouncedSearchUpdate(rawQuery = "", debounce = debounce, emit = { emitted += it })

        // No virtual time advanced and yet the empty value is already through.
        assertEquals(listOf(""), emitted)
    }

    @Test
    fun `Given non-empty query when debouncing then suspend until delay elapses before emitting`() = runTest {
        val emitted = mutableListOf<String>()

        val job = launch {
            debouncedSearchUpdate(rawQuery = "chrome", debounce = debounce, emit = { emitted += it })
        }

        // Before the debounce expires nothing has been emitted yet.
        advanceTimeBy(299.milliseconds)
        runCurrent()
        assertEquals(emptyList<String>(), emitted)

        // Crossing the boundary lets the emit happen.
        advanceTimeBy(2.milliseconds)
        runCurrent()
        assertEquals(listOf("chrome"), emitted)

        job.join()
    }

    @Test
    fun `Given rapid updates when each is cancelled before debounce then only final value is emitted`() = runTest {
        val emitted = mutableListOf<String>()

        // Simulates the LaunchedEffect(searchQueryRaw) restart behaviour: each new raw value
        // cancels the previous launch and starts a new one. Only the last one runs to completion.
        val first = launch {
            debouncedSearchUpdate(rawQuery = "c", debounce = debounce, emit = { emitted += it })
        }
        advanceTimeBy(100.milliseconds)
        first.cancel()

        val second = launch {
            debouncedSearchUpdate(rawQuery = "ch", debounce = debounce, emit = { emitted += it })
        }
        advanceTimeBy(100.milliseconds)
        second.cancel()

        val third = launch {
            debouncedSearchUpdate(rawQuery = "chr", debounce = debounce, emit = { emitted += it })
        }
        advanceTimeBy(debounce)
        runCurrent()

        assertEquals(listOf("chr"), emitted)
        third.join()
    }

    @Test
    fun `Given pending non-empty emit when cancelled before delay then nothing is emitted`() = runTest {
        val emitted = mutableListOf<String>()

        val job = launch {
            debouncedSearchUpdate(rawQuery = "chrome", debounce = debounce, emit = { emitted += it })
        }
        advanceTimeBy(100.milliseconds)
        job.cancel()
        runCurrent()

        assertEquals(emptyList<String>(), emitted)
    }

    @Test
    fun `Given empty query after a non-empty one when both run then empty bypasses debounce`() = runTest {
        val emitted = mutableListOf<String>()

        // Non-empty: pending until delay.
        val nonEmpty = launch {
            debouncedSearchUpdate(rawQuery = "chrome", debounce = debounce, emit = { emitted += it })
        }
        advanceTimeBy(50.milliseconds)
        nonEmpty.cancel()

        // Replaced by an empty raw value — should be reflected to the parent immediately.
        debouncedSearchUpdate(rawQuery = "", debounce = debounce, emit = { emitted += it })

        assertEquals(listOf(""), emitted)
    }
}
