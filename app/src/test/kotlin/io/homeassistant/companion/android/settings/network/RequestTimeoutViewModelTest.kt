package io.homeassistant.companion.android.settings.network

import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.common.data.RequestTimeoutRepository
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherJUnit5Extension::class)
class RequestTimeoutViewModelTest {
    private val storage = mockk<LocalStorage>()

    @Test
    fun `Given a saved timeout when opening settings then it is selected and a new selection is persisted`() = runTest {
        coEvery { storage.getLong(any()) } returns 15
        coJustRun { storage.putLong(any(), any()) }
        val viewModel = RequestTimeoutViewModel(RequestTimeoutRepository(storage))
        assertNull(viewModel.uiState.value.timeout)
        advanceUntilIdle()
        assertEquals(15.seconds, viewModel.uiState.value.timeout)

        viewModel.selectTimeout(10.seconds)
        assertTrue(viewModel.uiState.value.isSaving)
        advanceUntilIdle()

        assertEquals(10.seconds, viewModel.uiState.value.timeout)
        assertFalse(viewModel.uiState.value.isSaving)
        coVerify { storage.putLong("network_request_timeout_seconds", 10) }
    }

    @Test
    fun `Given a failed save when selecting a timeout then the old selection remains and an error is shown`() = runTest {
        coEvery { storage.getLong(any()) } returns null
        coEvery { storage.putLong(any(), any()) } throws IOException("Storage unavailable")
        val viewModel = RequestTimeoutViewModel(RequestTimeoutRepository(storage))
        advanceUntilIdle()

        viewModel.selectTimeout(5.seconds)
        advanceUntilIdle()

        assertEquals(30.seconds, viewModel.uiState.value.timeout)
        assertTrue(viewModel.uiState.value.hasError)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `Given a failed load when retrying then settings can load successfully`() = runTest {
        coEvery { storage.getLong(any()) } throws IOException("Storage unavailable")
        val viewModel = RequestTimeoutViewModel(RequestTimeoutRepository(storage))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.hasError)

        coEvery { storage.getLong(any()) } returns 10
        viewModel.load()
        advanceUntilIdle()

        assertEquals(10.seconds, viewModel.uiState.value.timeout)
        assertFalse(viewModel.uiState.value.hasError)
    }
}
