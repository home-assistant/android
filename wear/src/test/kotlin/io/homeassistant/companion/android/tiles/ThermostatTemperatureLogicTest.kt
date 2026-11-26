package io.homeassistant.companion.android.tiles

import org.junit.jupiter.api.Assertions.assertEquals
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
    fun `classic range preserves separation and respects limits`() {
        assertEquals(74f to 79f, ThermostatTemperatureLogic.shiftRange(1f, 73f, 78f, 50f, 90f, 1f))
        assertEquals(72f to 77f, ThermostatTemperatureLogic.shiftRange(-1f, 73f, 78f, 50f, 90f, 1f))
        assertEquals(85f to 90f, ThermostatTemperatureLogic.shiftRange(3f, 84f, 89f, 50f, 90f, 1f))
        assertEquals(50f to 55f, ThermostatTemperatureLogic.shiftRange(-3f, 51f, 56f, 50f, 90f, 1f))
        assertEquals(85f to 90f, ThermostatTemperatureLogic.shiftRange(1f, 85f, 90f, 50f, 90f, 1f))
        assertEquals(18.5f to 22.5f, ThermostatTemperatureLogic.shiftRange(0.5f, 18f, 22f, 5f, 30f, 0.5f))
    }
}
