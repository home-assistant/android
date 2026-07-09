package io.homeassistant.companion.android.settings.sensor.views

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SensorDetailSettingSheetFilterTest {

    private val entries = listOf(
        SettingEntry(id = "com.google.chrome", label = "Chrome\n(com.google.chrome)"),
        SettingEntry(id = "org.mozilla.firefox", label = "Firefox\n(org.mozilla.firefox)"),
        SettingEntry(id = "com.example.app", label = "Example App\n(com.example.app)"),
    )

    @Test
    fun `Given empty query when filtering then return all entries`() {
        val result = filterSettingEntries(entries, query = "")

        assertEquals(entries, result)
    }

    @Test
    fun `Given blank query when filtering then return all entries`() {
        val result = filterSettingEntries(entries, query = "   ")

        assertEquals(entries, result)
    }

    @Test
    fun `Given query matching app name when filtering then return matching entries`() {
        val result = filterSettingEntries(entries, query = "Chrome")

        assertEquals(listOf(entries[0]), result)
    }

    @Test
    fun `Given query matching package name in label when filtering then return matching entries`() {
        val result = filterSettingEntries(entries, query = "com.google")

        assertEquals(listOf(entries[0]), result)
    }

    @Test
    fun `Given case-insensitive query when filtering then return matches`() {
        val result = filterSettingEntries(entries, query = "CHROME")

        assertEquals(listOf(entries[0]), result)
    }

    @Test
    fun `Given query matching no entries when filtering then return empty list`() {
        val result = filterSettingEntries(entries, query = "nonexistent")

        assertEquals(emptyList<SettingEntry>(), result)
    }

    @Test
    fun `Given query with leading and trailing spaces when filtering then trim and match`() {
        val result = filterSettingEntries(entries, query = " Chrome ")

        assertEquals(listOf(entries[0]), result)
    }
}
