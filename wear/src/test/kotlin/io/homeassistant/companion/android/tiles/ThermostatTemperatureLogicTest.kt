package io.homeassistant.companion.android.tiles

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ThermostatTemperatureLogicTest {
    @Test
    fun `Given an entity step and bounds when snapping then round and clamp the temperature`() {
        assertEquals(72.5f, ThermostatTemperatureLogic.snap(72.3f, 0.5f, 50f, 90f))
        assertEquals(50f, ThermostatTemperatureLogic.snap(12f, 1f, 50f, 90f))
        assertEquals(90f, ThermostatTemperatureLogic.snap(110f, 1f, 50f, 90f))
    }

    @Test
    fun `Given an off-grid minimum when snapping then use the independent step grid`() {
        assertEquals(21.5f, ThermostatTemperatureLogic.snap(21.5f, 0.5f, 7.2f, 35f))
        assertEquals(7.2f, ThermostatTemperatureLogic.snap(6f, 0.5f, 7.2f, 35f))
        assertEquals(30.8f, ThermostatTemperatureLogic.snap(40f, 1f, 7.2f, 30.8f))
        assertEquals(
            SetpointRange(21.5f, 25f),
            ThermostatTemperatureLogic.adjustRange(21.5f, false, SetpointRange(21f, 25f), 7.2f, 35f, 0.5f),
        )
    }

    @Test
    fun `Given heating and cooling targets when adjusting either bound then prevent crossing`() {
        assertEquals(
            SetpointRange(50f, 70f),
            ThermostatTemperatureLogic.adjustRange(50f, false, SetpointRange(69f, 70f), 50f, 90f, 1f),
        )
        assertEquals(
            SetpointRange(69f, 70f),
            ThermostatTemperatureLogic.adjustRange(60f, true, SetpointRange(69f, 70f), 50f, 90f, 1f),
        )
        assertEquals(
            SetpointRange(69f, 80f),
            ThermostatTemperatureLogic.adjustRange(80f, true, SetpointRange(69f, 70f), 50f, 90f, 1f),
        )
    }

    @Test
    fun `Given a Classic range when shifting targets then preserve separation and respect limits`() {
        fun assertShift(delta: Float, initial: SetpointRange, expected: SetpointRange, step: Float = 1f) {
            assertEquals(expected, ThermostatTemperatureLogic.shiftRange(delta, initial, 50f, 90f, step))
        }
        assertShift(1f, SetpointRange(73f, 78f), SetpointRange(74f, 79f))
        assertShift(-1f, SetpointRange(73f, 78f), SetpointRange(72f, 77f))
        assertShift(3f, SetpointRange(84f, 89f), SetpointRange(85f, 90f))
        assertShift(-3f, SetpointRange(51f, 56f), SetpointRange(50f, 55f))
        assertShift(1f, SetpointRange(85f, 90f), SetpointRange(85f, 90f))
        assertEquals(
            SetpointRange(18.5f, 22.5f),
            ThermostatTemperatureLogic.shiftRange(0.5f, SetpointRange(18f, 22f), 5f, 30f, 0.5f),
        )
        assertShift(1f, SetpointRange(73f, 78f), SetpointRange(73f, 78f), step = 0f)
        for (invalid in listOf(SetpointRange(49f, 55f), SetpointRange(85f, 91f), SetpointRange(78f, 73f))) {
            assertShift(1f, invalid, invalid)
        }
    }
}
