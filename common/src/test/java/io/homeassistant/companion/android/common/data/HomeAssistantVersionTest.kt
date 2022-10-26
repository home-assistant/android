package io.homeassistant.companion.android.common.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeAssistantVersionTest {
    @Test
    fun fromInvalidStringTest() {
        assertNull(HomeAssistantVersion.fromString("foo"))
        assertNull(HomeAssistantVersion.fromString("2020.foo.bar"))
        assertNull(HomeAssistantVersion.fromString("10001.10.12"))
    }

    @Test
    fun fromDateVersionTest() {
        assertEquals(
            HomeAssistantVersion(2021, 10, 12),
            HomeAssistantVersion.fromString("2021.10.12")
        )
        assertEquals(
            HomeAssistantVersion(2023, 1, 1),
            HomeAssistantVersion.fromString("2023.1.01.dev")
        )
    }
}
