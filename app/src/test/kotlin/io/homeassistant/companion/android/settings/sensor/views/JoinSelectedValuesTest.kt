package io.homeassistant.companion.android.settings.sensor.views

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JoinSelectedValuesTest {

    @Test
    fun `Given empty list when joining then return empty string`() {
        val result = joinSelectedValues(emptyList())

        assertEquals("", result)
    }

    @Test
    fun `Given single value when joining then return value alone without separators`() {
        val result = joinSelectedValues(listOf("com.google.chrome"))

        assertEquals("com.google.chrome", result)
    }

    @Test
    fun `Given multiple values when joining then comma-space separated without surrounding brackets`() {
        val result = joinSelectedValues(
            listOf("com.google.chrome", "org.mozilla.firefox", "com.example.app"),
        )

        assertEquals("com.google.chrome, org.mozilla.firefox, com.example.app", result)
    }

    @Test
    fun `Given values containing brackets when joining then brackets pass through unchanged`() {
        val result = joinSelectedValues(listOf("foo[bar]", "baz[qux"))

        assertEquals("foo[bar], baz[qux", result)
    }

    @Test
    fun `Given values containing commas and unicode when joining then values pass through unchanged`() {
        val result = joinSelectedValues(listOf("hello,world", "café-☕", "id-中文"))

        assertEquals("hello,world, café-☕, id-中文", result)
    }
}
