package io.homeassistant.companion.android.common.util

import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class MdiIconsTest {

    @Nested
    inner class MdiName {
        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource(
            "cmd_account, mdi:account",
            "cmd_account_alert, mdi:account-alert",
            "cmd_weather_partly_cloudy, mdi:weather-partly-cloudy",
        )
        fun `Given an iconics icon when getting the mdi name then the prefix and separators are converted`(
            iconicsName: String,
            expectedMdiName: String,
        ) {
            val icon = requireNotNull(CommunityMaterial.getIcon(iconicsName))

            assertEquals(expectedMdiName, icon.mdiName)
        }
    }

    @Nested
    inner class GetIconByMdiName {
        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = ["mdi:account", "mdi:account-alert", "mdi:weather-partly-cloudy"])
        fun `Given a known mdi name when resolving the icon then it round-trips back to the same name`(mdiName: String) {
            val icon = CommunityMaterial.getIconByMdiName(mdiName)

            assertEquals(mdiName, icon?.mdiName)
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = ["mdi:", "mdi:abcdefgh"])
        fun `Given an unknown mdi name when resolving the icon then returns null`(mdiName: String) {
            assertNull(CommunityMaterial.getIconByMdiName(mdiName))
        }

        @Test
        fun `Given an underscore separated mdi name when resolving the icon then it still resolves`() {
            // Only dashes are translated, so a name already in the Iconics separator form passes through
            assertEquals("mdi:account-alert", CommunityMaterial.getIconByMdiName("mdi:account_alert")?.mdiName)
        }

        @Test
        fun `Given a name without the mdi prefix when resolving the icon then returns null`() {
            // Home Assistant always sends the prefix; without it the lookup must not silently succeed
            assertNull(CommunityMaterial.getIconByMdiName("account"))
        }

        @Test
        fun `Given an empty name when resolving the icon then returns null`() {
            assertNull(CommunityMaterial.getIconByMdiName(""))
        }
    }
}
