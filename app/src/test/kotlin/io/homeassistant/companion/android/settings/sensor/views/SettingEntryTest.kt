package io.homeassistant.companion.android.settings.sensor.views

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SettingEntryTest {

    @Test
    fun `Given single-line label when creating entry then secondary is null`() {
        assertLabelSplitsInto(label = "Chrome", expectedPrimary = "Chrome", expectedSecondary = null)
    }

    @Test
    fun `Given two-line label with parens when creating entry then parens are stripped from secondary`() {
        assertLabelSplitsInto(
            label = "Chrome\n(com.google.chrome)",
            expectedPrimary = "Chrome",
            expectedSecondary = "com.google.chrome",
        )
    }

    @Test
    fun `Given label with multiple newlines when creating entry then extra lines stay inside secondary`() {
        assertLabelSplitsInto(
            label = "Chrome\nline1\nline2",
            expectedPrimary = "Chrome",
            expectedSecondary = "line1\nline2",
        )
    }

    @Test
    fun `Given second line without parens when creating entry then secondary is kept verbatim`() {
        assertLabelSplitsInto(label = "Foo\nbar", expectedPrimary = "Foo", expectedSecondary = "bar")
    }

    @Test
    fun `Given mismatched leading paren when creating entry then paren is preserved`() {
        assertLabelSplitsInto(label = "Foo\n(bar", expectedPrimary = "Foo", expectedSecondary = "(bar")
    }

    @Test
    fun `Given empty label when creating entry then primary is empty and secondary is null`() {
        assertLabelSplitsInto(label = "", expectedPrimary = "", expectedSecondary = null)
    }

    @Test
    fun `Given empty second line when creating entry then secondary is empty`() {
        assertLabelSplitsInto(label = "Foo\n", expectedPrimary = "Foo", expectedSecondary = "")
    }

    private fun assertLabelSplitsInto(label: String, expectedPrimary: String, expectedSecondary: String?) {
        val entry = SettingEntry(id = "id", label = label)

        assertEquals(expectedPrimary, entry.primary)
        assertEquals(expectedSecondary, entry.secondary)
    }
}
