package io.homeassistant.companion.android.changelog

import io.homeassistant.companion.android.common.modes.HomeAssistantMode
import io.homeassistant.companion.android.frontend.navigation.WidgetType
import io.homeassistant.companion.android.settings.SettingsActivity
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ChangelogActionAvailabilityTest {

    private fun availability(homeAssistantModeSupported: Boolean) = ChangelogActionAvailability(
        mockk<HomeAssistantMode> { every { isSupported() } returns homeAssistantModeSupported },
    )

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `Given Modes support when checking the Home Assistant mode setting then it is available only with Modes`(supported: Boolean) {
        val action = ChangelogAction.OpenSettings(SettingsActivity.Deeplink.HomeAssistantMode)

        assertEquals(supported, availability(supported).isAvailable(action))
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `Given Modes support when checking actions available everywhere then they are available`(supported: Boolean) {
        val actions = listOf(
            ChangelogAction.OpenSettings(SettingsActivity.Deeplink.Gestures),
            ChangelogAction.OpenUrl("https://www.home-assistant.io"),
            ChangelogAction.OpenWidgetConfig(WidgetType.MediaPlayer),
            ChangelogAction.OpenAssist,
        )

        assertTrue(actions.all { availability(supported).isAvailable(it) })
    }
}
