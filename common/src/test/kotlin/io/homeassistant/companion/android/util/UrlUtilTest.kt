package io.homeassistant.companion.android.util

import java.net.URL
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UrlUtilTest {
    @Test
    fun `handle should return absolute url when navigate prefix present`() {
        val base = URL("https://base.example")
        val input = "homeassistant://navigate/https://www.example.com/path"
        val expected = URL("https://www.example.com/path")

        val result = UrlUtil.handle(base, input)

        assertEquals(expected, result)
    }
}
