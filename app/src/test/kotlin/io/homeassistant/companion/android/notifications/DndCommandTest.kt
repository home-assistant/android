package io.homeassistant.companion.android.notifications

import io.homeassistant.companion.android.common.sensors.DNDSensorManager
import io.homeassistant.companion.android.common.sensors.HomeAssistantMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class DndCommandTest {

    private val dndSensorManager = mockk<DNDSensorManager> {
        coEvery { setHomeAssistantModeActive(any()) } returns HomeAssistantMode.ChangeResult.Applied
    }

    @ParameterizedTest(name = "{0} with mode supported={1} -> {2}")
    @CsvSource(
        // Existing commands do not depend on the mode
        "priority_only, false, Apply",
        "off, true, Apply",
        "mode_on, true, Apply",
        "mode_off, true, Apply",
        // Ignored rather than posted as a notification on devices without the mode
        "mode_on, false, IgnoreUnsupported",
        "mode_off, false, IgnoreUnsupported",
        "unknown, true, Invalid",
        // An empty value is a missing command
        ", true, Invalid",
    )
    fun `Given a DND command when deciding how to handle it then the expected handling is returned`(
        command: String?,
        modeSupported: Boolean,
        expected: String,
    ) {
        assertEquals(expected, dndCommandHandling(command, modeSupported).toString())
    }

    @ParameterizedTest(name = "{0} -> active={1}")
    @CsvSource("mode_on, true", "mode_off, false")
    fun `Given a mode command when applying it then the mode is turned on or off`(command: String, active: Boolean) = runTest {
        applyHomeAssistantModeCommand(command, dndSensorManager)

        coVerify(exactly = 1) { dndSensorManager.setHomeAssistantModeActive(active) }
    }
}
