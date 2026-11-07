package io.homeassistant.companion.android.common

import android.content.SharedPreferences
import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
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

    @Test
    fun `Given observing multiple keys when any watched key changes then the changed key is emitted`() = runTest {
        localStorage.observeChanges("first_key", "second_key").test {
            listenerSlot.captured.onSharedPreferenceChanged(sharedPreferences, "first_key")
            assertEquals("first_key", awaitItem())
            listenerSlot.captured.onSharedPreferenceChanged(sharedPreferences, "second_key")
            assertEquals("second_key", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given values to store and to drop when putting them together then one editor applies all of them`() = runTest {
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { sharedPreferences.edit() } returns editor

        localStorage.putStrings(mapOf("kept_key" to "value", "dropped_key" to null))

        // A single editor is the whole point: the keys must be committed together.
        verify(exactly = 1) { sharedPreferences.edit() }
        verify(exactly = 1) { editor.putString("kept_key", "value") }
        verify(exactly = 1) { editor.remove("dropped_key") }
        verify(exactly = 1) { editor.apply() }
    }

    @Test
    fun `Given no values when putting them together then nothing is written`() = runTest {
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { sharedPreferences.edit() } returns editor

        localStorage.putStrings(emptyMap())

        verify(exactly = 0) { editor.putString(any(), any()) }
        verify(exactly = 0) { editor.remove(any()) }
    }
}
