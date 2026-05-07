package io.homeassistant.companion.android.common

import android.content.SharedPreferences
import app.cash.turbine.test
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(ConsoleLogExtension::class)
class LocalStorageImplTest {
    private val listenerSlot = slot<SharedPreferences.OnSharedPreferenceChangeListener>()
    private val sharedPreferences: SharedPreferences = mockk(relaxed = true) {
        every { registerOnSharedPreferenceChangeListener(capture(listenerSlot)) } returns Unit
    }
    private val localStorage = LocalStorageImpl { sharedPreferences }

    @Test
    fun `Given observing key when matching key changes then key is emitted`() = runTest {
        localStorage.observeChanges("my_key").test {
            listenerSlot.captured.onSharedPreferenceChanged(sharedPreferences, "my_key")
            assertEquals("my_key", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given observing key when different key changes then nothing is emitted`() = runTest {
        localStorage.observeChanges("my_key").test {
            listenerSlot.captured.onSharedPreferenceChanged(sharedPreferences, "other_key")
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given observing key when cancelled then listener is unregistered`() = runTest {
        localStorage.observeChanges("my_key").test {
            cancelAndIgnoreRemainingEvents()
        }
        verify { sharedPreferences.unregisterOnSharedPreferenceChangeListener(any()) }
    }

    @Test
    fun `Given observing keys with mapper when subscribing then mapper result is emitted immediately`() = runTest {
        localStorage.observeChanges("my_key") { "mapped_value" }.test {
            assertEquals("mapped_value", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
