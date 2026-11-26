package io.homeassistant.companion.android.tiles

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ThermostatTemperatureLogicTest {
    @Test
    fun `snap clamps to allowed limits and uses entity step`() {
        assertEquals(72.5f, ThermostatTemperatureLogic.snap(72.3f, 0.5f, 50f, 90f))
        assertEquals(50f, ThermostatTemperatureLogic.snap(12f, 1f, 50f, 90f))
        assertEquals(90f, ThermostatTemperatureLogic.snap(110f, 1f, 50f, 90f))
    }

    @Test
    fun `heat cooling range never crosses`() {
        assertEquals(
            50f to 70f,
            ThermostatTemperatureLogic.adjustRange(50f, false, 69f, 70f, 50f, 90f, 1f),
        )
        assertEquals(
            69f to 70f,
            ThermostatTemperatureLogic.adjustRange(60f, true, 69f, 70f, 50f, 90f, 1f),
        )
        assertEquals(
            69f to 80f,
            ThermostatTemperatureLogic.adjustRange(80f, true, 69f, 70f, 50f, 90f, 1f),
        )
    }

    @Test
    fun `top arc gestures are left to right and clamped`() {
        assertEquals(0.5f, ThermostatTemperatureLogic.fractionFromTopArc(0f, -1f, 0f, 0f), 0.01f)
        assertEquals(0.5f, ThermostatTemperatureLogic.normalized(70f, 50f, 90f))
    }

    @Test
    fun `nearest heat and cool handle is selected`() {
        assertFalse(ThermostatTemperatureLogic.chooseHighMarker(0.25f, 0.2f, 0.8f))
        assertTrue(ThermostatTemperatureLogic.chooseHighMarker(0.75f, 0.2f, 0.8f))
    }
}
