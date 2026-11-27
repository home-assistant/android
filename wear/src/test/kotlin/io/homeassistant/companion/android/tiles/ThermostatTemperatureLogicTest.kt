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
        assertEquals(
            SetpointRange(74f, 79f),
            ThermostatTemperatureLogic.shiftRange(1f, SetpointRange(73f, 78f), 50f, 90f, 1f),
        )
        assertEquals(
            SetpointRange(72f, 77f),
            ThermostatTemperatureLogic.shiftRange(-1f, SetpointRange(73f, 78f), 50f, 90f, 1f),
        )
        assertEquals(
            SetpointRange(85f, 90f),
            ThermostatTemperatureLogic.shiftRange(3f, SetpointRange(84f, 89f), 50f, 90f, 1f),
        )
        assertEquals(
            SetpointRange(50f, 55f),
            ThermostatTemperatureLogic.shiftRange(-3f, SetpointRange(51f, 56f), 50f, 90f, 1f),
        )
        assertEquals(
            SetpointRange(85f, 90f),
            ThermostatTemperatureLogic.shiftRange(1f, SetpointRange(85f, 90f), 50f, 90f, 1f),
        )
        assertEquals(
            SetpointRange(18.5f, 22.5f),
            ThermostatTemperatureLogic.shiftRange(0.5f, SetpointRange(18f, 22f), 5f, 30f, 0.5f),
        )
        assertEquals(
            SetpointRange(73f, 78f),
            ThermostatTemperatureLogic.shiftRange(1f, SetpointRange(73f, 78f), 50f, 90f, 0f),
        )
        assertEquals(
            SetpointRange(49f, 55f),
            ThermostatTemperatureLogic.shiftRange(1f, SetpointRange(49f, 55f), 50f, 90f, 1f),
        )
        assertEquals(
            SetpointRange(85f, 91f),
            ThermostatTemperatureLogic.shiftRange(1f, SetpointRange(85f, 91f), 50f, 90f, 1f),
        )
        assertEquals(
            SetpointRange(78f, 73f),
            ThermostatTemperatureLogic.shiftRange(1f, SetpointRange(78f, 73f), 50f, 90f, 1f),
        )
    }
}
