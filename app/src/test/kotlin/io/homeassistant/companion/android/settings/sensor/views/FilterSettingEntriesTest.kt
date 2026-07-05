package io.homeassistant.companion.android.settings.sensor.views

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class FilterSettingEntriesTest {

    private val entries = listOf(
        "com.google.android.apps.maps" to "Google Maps\n(com.google.android.apps.maps)",
        "com.google.android.calendar" to "Google Calendar\n(com.google.android.calendar)",
        "com.coloros.alarmclock" to "Clock\n(com.coloros.alarmclock)",
        "org.mozilla.firefox" to "Firefox\n(org.mozilla.firefox)",
    )

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "   ", "\t"])
    fun `Given a blank query when filtering then all entries are returned`(query: String) {
        assertEquals(entries, filterSettingEntries(entries = entries, query = query))
    }

    @Test
    fun `Given a single term when filtering then labels are matched case-insensitively`() {
        val result = filterSettingEntries(entries = entries, query = "FIREFOX")

        assertEquals(listOf(entries[3]), result)
    }

    @Test
    fun `Given multiple terms when filtering then only entries matching every term are returned`() {
        val result = filterSettingEntries(entries = entries, query = "google maps")

        assertEquals(listOf(entries[0]), result)
    }

    @Test
    fun `Given multiple terms when filtering then term order does not matter`() {
        val result = filterSettingEntries(entries = entries, query = "maps goo")

        assertEquals(listOf(entries[0]), result)
    }

    @Test
    fun `Given surrounding and repeated whitespace when filtering then terms are still applied`() {
        val result = filterSettingEntries(entries = entries, query = "  google   maps ")

        assertEquals(listOf(entries[0]), result)
    }

    @Test
    fun `Given a term matching several labels when filtering then all matching entries are returned in order`() {
        val result = filterSettingEntries(entries = entries, query = "google")

        assertEquals(listOf(entries[0], entries[1]), result)
    }

    @Test
    fun `Given a term without matches when filtering then no entries are returned`() {
        val result = filterSettingEntries(entries = entries, query = "does not exist")

        assertEquals(emptyList<Pair<String, String>>(), result)
    }
}
