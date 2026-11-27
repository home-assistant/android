package io.homeassistant.companion.android.tiles

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ThermostatDisplayUnitsTest {
    @Test
    fun `absolute values convert in both directions without changing the baseline`() {
        val fahrenheit = ThermostatDisplayUnits("°C", "F")
        val celsius = ThermostatDisplayUnits("°F", "C")
        assertEquals(73.4f, fahrenheit.display(23f))
        assertEquals(23f, celsius.display(73.4f))
        assertEquals(32f, fahrenheit.display(0f))
        assertEquals(-40f, celsius.display(-40f))
        assertEquals(null, celsius.display(null))
        assertEquals("73.4° F", fahrenheit.enhancedText("73.4"))
        assertEquals("23° C", celsius.enhancedText("23"))
    }

    @Test
    fun `relative adjustments convert without applying the absolute temperature offset`() {
        val fahrenheit = ThermostatDisplayUnits("°C", "F")
        val celsius = ThermostatDisplayUnits("°F", "C")
        assertEquals(5f / 9f, fahrenheit.adjustment(1f, 0.5f), 0.0001f)
        assertEquals(-5f, fahrenheit.adjustment(-9f, 0.5f), 0.0001f)
        assertEquals(1.8f, celsius.adjustment(1f, 1f), 0.0001f)
        assertEquals(-5.4f, celsius.adjustment(-3f, 1f), 0.0001f)
        assertEquals(1f, fahrenheit.adjustment(1f, 1f))
        assertEquals(-1f, fahrenheit.adjustment(-1f, 1f))
    }

    @Test
    fun `converted adjustments retain server bounds and range separation`() {
        val units = ThermostatDisplayUnits("°F", "C")
        val step = 1f
        assertEquals(75f, ThermostatTemperatureLogic.snap(73f + units.adjustment(1f, step), step, 45f, 95f))
        assertEquals(95f, ThermostatTemperatureLogic.snap(94f + units.adjustment(5f, step), step, 45f, 95f))
        assertEquals(
            SetpointRange(77f, 78f),
            ThermostatTemperatureLogic.adjustRange(
                75f + units.adjustment(3f, step),
                false,
                SetpointRange(75f, 78f),
                45f,
                95f,
                step,
            ),
        )
    }

    @Test
    fun `default and unknown units respect larger server steps in both directions`() {
        val default = ThermostatDisplayUnits("°C", "default")
        val unknown = ThermostatDisplayUnits(null, "F")
        for (units in listOf(default, unknown)) {
            assertEquals(24f, ThermostatTemperatureLogic.snap(22f + units.adjustment(1f, 2f), 2f, 10f, 40f))
            assertEquals(20f, ThermostatTemperatureLogic.snap(22f + units.adjustment(-1f, 2f), 2f, 10f, 40f))
            assertEquals(0f, units.adjustment(0f, 2f))
        }
    }

    @Test
    fun `default and unavailable source unit preserve original values and actions`() {
        val default = ThermostatDisplayUnits("°C", "default")
        val unknown = ThermostatDisplayUnits(null, "F")
        assertFalse(default.hasOverride)
        assertFalse(unknown.hasOverride)
        assertEquals(21.5f, default.display(21.5f))
        assertEquals(21.5f, unknown.display(21.5f))
        assertEquals("°", unknown.symbol)
        assertEquals(2f, unknown.adjustment(1f, 2f))
        assertEquals("target|", default.actionPrefix("target|"))
        assertEquals("target|", unknown.actionPrefix("target|"))
        val fahrenheit = ThermostatDisplayUnits("°C", "F")
        assertTrue(fahrenheit.hasOverride)
        assertEquals("target|°F:", fahrenheit.actionPrefix("target|"))
    }
}
