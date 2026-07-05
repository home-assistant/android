package io.homeassistant.companion.android.settings.sensor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SortEntriesSelectedFirstTest {

    private val alpha = "com.example.alpha" to "Alpha"
    private val bravo = "com.example.bravo" to "Bravo"
    private val charlie = "com.example.charlie" to "Charlie"
    private val delta = "com.example.delta" to "Delta"
    private val entries = listOf(alpha, bravo, charlie, delta)

    @Test
    fun `Given selected entries when sorting then they are moved to the front keeping order within groups`() {
        val result = sortEntriesSelectedFirst(
            entries = entries,
            entriesSelected = listOf(delta.first, bravo.first),
            singleSelect = false,
        )

        assertEquals(listOf(bravo, delta, alpha, charlie), result)
    }

    @Test
    fun `Given no selected entries when sorting then the list is unchanged`() {
        val result = sortEntriesSelectedFirst(
            entries = entries,
            entriesSelected = emptyList(),
            singleSelect = false,
        )

        assertEquals(entries, result)
    }

    @Test
    fun `Given all entries selected when sorting then the list is unchanged`() {
        val result = sortEntriesSelectedFirst(
            entries = entries,
            entriesSelected = entries.map { it.first },
            singleSelect = false,
        )

        assertEquals(entries, result)
    }

    @Test
    fun `Given a single-select list when sorting then the list is unchanged even with a selection`() {
        val result = sortEntriesSelectedFirst(
            entries = entries,
            entriesSelected = listOf(charlie.first),
            singleSelect = true,
        )

        assertEquals(entries, result)
    }

    @Test
    fun `Given selected IDs not present in the entries when sorting then they are ignored`() {
        val result = sortEntriesSelectedFirst(
            entries = entries,
            entriesSelected = listOf("com.example.unknown", charlie.first),
            singleSelect = false,
        )

        assertEquals(listOf(charlie, alpha, bravo, delta), result)
    }

    @Test
    fun `Given a selection when sorting then matching is done on entry ID and not on label`() {
        val result = sortEntriesSelectedFirst(
            entries = entries,
            entriesSelected = listOf("Bravo"),
            singleSelect = false,
        )

        assertEquals(entries, result)
    }
}
