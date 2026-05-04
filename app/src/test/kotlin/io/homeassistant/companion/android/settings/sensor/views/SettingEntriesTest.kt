package io.homeassistant.companion.android.settings.sensor.views

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the contract of the [SettingEntries] wrapper introduced for Compose stability.
 *
 * The Compose Compiler cannot infer stability for `List<Pair<String, String>>`, so this wrapper
 * is annotated `@Immutable` and used as the parameter type of `rememberFilteredSettingEntries`.
 * These tests exercise the wrapper so a future regression (e.g. accidentally removing the
 * wrapper or changing its shape) fails loudly.
 */
class SettingEntriesTest {

    private val sampleItems = listOf(
        "com.google.chrome" to "Chrome\n(com.google.chrome)",
        "org.mozilla.firefox" to "Firefox\n(org.mozilla.firefox)",
    )

    @Test
    fun `Given list when wrapping then items property exposes the same list reference`() {
        val wrapper = SettingEntries(sampleItems)

        assertSame(sampleItems, wrapper.items)
    }

    @Test
    fun `Given two wrappers built from equal lists when compared then they are equal`() {
        val first = SettingEntries(sampleItems)
        val second = SettingEntries(sampleItems.toList())

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun `Given two wrappers built from different lists when compared then they are not equal`() {
        val first = SettingEntries(sampleItems)
        val second = SettingEntries(emptyList())

        assertNotEquals(first, second)
    }

    @Test
    fun `Given wrapper when filtered through filterSettingEntries then matches plain list filtering`() {
        val wrapper = SettingEntries(sampleItems)

        val filtered = filterSettingEntries(wrapper.items, query = "Chrome")

        assertEquals(listOf(sampleItems[0]), filtered)
    }

    @Test
    fun `Given empty list when wrapping then items is empty`() {
        val wrapper = SettingEntries(emptyList())

        assertTrue(wrapper.items.isEmpty())
    }
}
