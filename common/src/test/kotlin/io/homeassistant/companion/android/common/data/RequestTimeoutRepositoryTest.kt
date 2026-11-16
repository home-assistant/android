package io.homeassistant.companion.android.common.data

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class RequestTimeoutRepositoryTest {
    private val storage = mockk<LocalStorage>()
    private val repository = RequestTimeoutRepository(storage)

    @Test
    fun `Given no saved setting when loading then the existing thirty second default is used`() = runTest {
        coEvery { storage.getLong(any()) } returns null

        repository.load()

        assertEquals(30.seconds, repository.timeout.value)
    }

    @ParameterizedTest
    @ValueSource(longs = [5, 10, 15, 30, 60])
    fun `Given a saved timeout when loading twice then it is restored with one storage read`(seconds: Long) = runTest {
        coEvery { storage.getLong("network_request_timeout_seconds") } returns seconds

        repository.load()
        repository.load()

        assertEquals(seconds.seconds, repository.timeout.value)
        coVerify(exactly = 1) { storage.getLong("network_request_timeout_seconds") }
    }

    @ParameterizedTest
    @ValueSource(longs = [-1, 0, 1, 31, 120, Long.MAX_VALUE])
    fun `Given an unsupported stored timeout when loading then the default is used`(seconds: Long) = runTest {
        coEvery { storage.getLong(any()) } returns seconds

        repository.load()

        assertEquals(30.seconds, repository.timeout.value)
    }

    @Test
    fun `Given a new setting before load when saved then persistence and observers receive it`() = runTest {
        coJustRun { storage.putLong(any(), any()) }

        repository.timeout.test {
            assertEquals(30.seconds, awaitItem())
            repository.setTimeout(10.seconds)
            assertEquals(10.seconds, awaitItem())
            repository.load()
            expectNoEvents()
        }

        coVerify { storage.putLong("network_request_timeout_seconds", 10) }
        coVerify(exactly = 0) { storage.getLong(any()) }
    }

    @Test
    fun `Given storage failure when saving then the active timeout remains unchanged`() {
        coEvery { storage.putLong(any(), any()) } throws IOException("Storage unavailable")

        assertThrows(IOException::class.java) { runTest { repository.setTimeout(10.seconds) } }

        assertEquals(30.seconds, repository.timeout.value)
    }

    @Test
    fun `Given an unsupported timeout when saving then it is rejected without changing storage`() {
        assertThrows(IllegalArgumentException::class.java) { runTest { repository.setTimeout(0.seconds) } }
        coVerify(exactly = 0) { storage.putLong(any(), any()) }
        assertEquals(30.seconds, repository.timeout.value)
    }

    @Test
    fun `Given a save in progress when the caller is cancelled then the committed value is still applied`() = runTest {
        val writing = CompletableDeferred<Unit>()
        val finishWrite = CompletableDeferred<Unit>()
        coEvery { storage.putLong(any(), any()) } coAnswers {
            writing.complete(Unit)
            finishWrite.await()
        }
        val save = launch { repository.setTimeout(10.seconds) }
        writing.await()

        save.cancel()
        finishWrite.complete(Unit)
        save.join()

        assertEquals(10.seconds, repository.timeout.value)
        coVerify(exactly = 1) { storage.putLong("network_request_timeout_seconds", 10) }
    }
}
