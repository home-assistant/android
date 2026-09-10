package io.homeassistant.companion.android.common.util

import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.AccountAlert
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class MdiIconsTest {

    @Test
    fun `Given an icon when getting the mdi name then the Home Assistant prefix is added`() {
        assertEquals("mdi:account-alert", Mdi.AccountAlert.mdiName)
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["mdi:account", "mdi:account-alert", "mdi:weather-partly-cloudy"])
    fun `Given a known prefixed name when resolving the icon then it round-trips back to the same name`(haName: String) {
        val icon = Mdi.fromHaName(haName)

        assertEquals(haName, icon?.mdiName)
    }

    @Test
    fun `Given a name without the mdi prefix when resolving the icon then it still resolves`() {
        assertEquals(Mdi.AccountAlert, Mdi.fromHaName("account-alert"))
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["mdi:", "mdi:abcdefgh", ""])
    fun `Given an unknown name when resolving the icon then returns null`(haName: String) {
        assertNull(Mdi.fromHaName(haName))
    }
}
