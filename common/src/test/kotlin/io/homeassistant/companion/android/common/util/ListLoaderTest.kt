package io.homeassistant.companion.android.common.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ListLoaderTest {

    @Test
    fun `Given loader returning a list when loadListOrEmpty then returns the list`() = runTest {
        val result = loadListOrEmpty("test data") { listOf(1, 2, 3) }

        assertEquals(listOf(1, 2, 3), result)
    }

    @Test
    fun `Given loader returning null when loadListOrEmpty then returns empty list`() = runTest {
        val result = loadListOrEmpty<Int>("test data") { null }

        assertEquals(emptyList<Int>(), result)
    }

    @Test
    fun `Given loader throwing when loadListOrEmpty then returns empty list`() = runTest {
        val result = loadListOrEmpty<Int>("test data") { throw IllegalStateException("boom") }

        assertEquals(emptyList<Int>(), result)
    }

    @Test
    fun `Given loader throwing CancellationException when loadListOrEmpty then rethrows`() = runTest {
        val result = runCatching {
            loadListOrEmpty<Int>("test data") { throw CancellationException("cancelled") }
        }

        assertTrue(result.exceptionOrNull() is CancellationException)
    }
}
