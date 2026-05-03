package io.homeassistant.companion.android.settings.sensor.views

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ParseSettingLabelTest {

    @Test
    fun `Given single-line label when parsing then primary equals label and secondary is null`() {
        val (primary, secondary) = parseSettingLabel("Chrome")

        assertEquals("Chrome", primary)
        assertNull(secondary)
    }

    @Test
    fun `Given two-line label with parens when parsing then strip parens from secondary`() {
        val (primary, secondary) = parseSettingLabel("Chrome\n(com.google.chrome)")

        assertEquals("Chrome", primary)
        assertEquals("com.google.chrome", secondary)
    }

    @Test
    fun `Given label with multiple newlines when parsing then only first split is used`() {
        // limit = 2 means subsequent newlines remain inside the secondary value.
        val (primary, secondary) = parseSettingLabel("Chrome\nline1\nline2")

        assertEquals("Chrome", primary)
        assertEquals("line1\nline2", secondary)
    }

    @Test
    fun `Given second line without parens when parsing then secondary is the raw line`() {
        val (primary, secondary) = parseSettingLabel("Foo\nbar")

        assertEquals("Foo", primary)
        assertEquals("bar", secondary)
    }

    @Test
    fun `Given mismatched parens when parsing then removeSurrounding is a no-op`() {
        // String.removeSurrounding only strips when both prefix and suffix match.
        val (primary, secondary) = parseSettingLabel("Foo\n(bar")

        assertEquals("Foo", primary)
        assertEquals("(bar", secondary)
    }

    @Test
    fun `Given empty label when parsing then primary is empty and secondary is null`() {
        val (primary, secondary) = parseSettingLabel("")

        assertEquals("", primary)
        assertNull(secondary)
    }

    @Test
    fun `Given empty second line when parsing then secondary is empty string`() {
        val (primary, secondary) = parseSettingLabel("Foo\n")

        assertEquals("Foo", primary)
        assertEquals("", secondary)
    }
}
