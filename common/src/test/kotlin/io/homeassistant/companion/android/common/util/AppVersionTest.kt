package io.homeassistant.companion.android.common.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppVersionTest {

    @Test
    fun `Given JSON when deserialize to AppVersion then it works`() {
        val source = "\"hello world (1)\""
        val appVersion = kotlinJsonMapper.decodeFromString<AppVersion>(source)
        assertEquals("hello world (1)", appVersion.value)
    }

    @Test
    fun `Given AppVersion when serialize to JSON then it works`() {
        val source = AppVersion.from("hello world", 1)
        val json = kotlinJsonMapper.encodeToString(source)
        assertEquals("\"hello world (1)\"", json)
    }
}
