package io.homeassistant.companion.android.common.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppVersionTest {

    @Test
    fun `Given JSON when deserialize to AppVersion then it works`() {
        val source = "\"hello world (1)\""
        val appVersion = kotlinJsonMapper.decodeFromString<AppVersion>(source)
        assertEquals(AppVersion(name = "hello world", code = 1), appVersion)
    }

    @Test
    fun `Given AppVersion when serialize to JSON then it works`() {
        val source = AppVersion(name = "hello world", code = 1)
        val json = kotlinJsonMapper.encodeToString(source)
        assertEquals("\"hello world (1)\"", json)
    }

    @Test
    fun `Given raw version with parentheses in the name when parsing then only the last suffix is the code`() {
        val appVersion = AppVersion.from("2025.8.1 (beta) (1250)")
        assertEquals("2025.8.1 (beta)", appVersion.name)
        assertEquals(1250, appVersion.code)
    }

    @Test
    fun `Given invalid raw version when parsing then it fails fast and falls back to the raw name with unknown code`() {
        var failFastTriggered = false
        FailFast.setHandler { _, _ -> failFastTriggered = true }
        try {
            val appVersion = AppVersion.from("garbage")
            assertEquals(AppVersion(name = "garbage", code = UNKNOWN_VERSION_CODE), appVersion)
            assertEquals(true, failFastTriggered)
        } finally {
            FailFast.setHandler(DefaultFailFastHandler)
        }
    }
}
