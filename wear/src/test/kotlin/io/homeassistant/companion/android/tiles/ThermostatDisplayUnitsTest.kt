package io.homeassistant.companion.android.tiles

import io.homeassistant.companion.android.database.wear.ThermostatTemperatureUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ThermostatDisplayUnitsTest {
    @Test
    fun `Given Celsius and Fahrenheit values when converting absolute temperatures then preserve the baseline`() {
        val fahrenheit = ThermostatDisplayUnits("°C", ThermostatTemperatureUnit.FAHRENHEIT)
        val celsius = ThermostatDisplayUnits("°F", ThermostatTemperatureUnit.CELSIUS)
        assertEquals(73.4f, fahrenheit.display(23f))
        assertEquals(23f, celsius.display(73.4f))
        assertEquals(32f, fahrenheit.display(0f))
        assertEquals(-40f, celsius.display(-40f))
        assertEquals(null, celsius.display(null))
        assertEquals("73.4° F", fahrenheit.enhancedText("73.4"))
        assertEquals("23° C", celsius.enhancedText("23"))
    }

    @Test
    fun `Given relative adjustments when converting units then omit the absolute offset`() {
        val fahrenheit = ThermostatDisplayUnits("°C", ThermostatTemperatureUnit.FAHRENHEIT)
        val celsius = ThermostatDisplayUnits("°F", ThermostatTemperatureUnit.CELSIUS)
        assertEquals(5f / 9f, fahrenheit.adjustment(1f, 0.5f), 0.0001f)
        assertEquals(-5f, fahrenheit.adjustment(-9f, 0.5f), 0.0001f)
        assertEquals(1.8f, celsius.adjustment(1f, 1f), 0.0001f)
        assertEquals(-5.4f, celsius.adjustment(-3f, 1f), 0.0001f)
        assertEquals(1f, fahrenheit.adjustment(1f, 1f))
        assertEquals(-1f, fahrenheit.adjustment(-1f, 1f))
    }

    @Test
    fun `Given converted adjustments when calculating targets then respect bounds and range separation`() {
        val units = ThermostatDisplayUnits("°F", ThermostatTemperatureUnit.CELSIUS)
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
    fun `Given default or unknown units when adjusting then respect larger server steps in either direction`() {
        val default = ThermostatDisplayUnits("°C", ThermostatTemperatureUnit.DEFAULT)
        val unknown = ThermostatDisplayUnits(null, ThermostatTemperatureUnit.FAHRENHEIT)
        for (units in listOf(default, unknown)) {
            assertEquals(24f, ThermostatTemperatureLogic.snap(22f + units.adjustment(1f, 2f), 2f, 10f, 40f))
            assertEquals(20f, ThermostatTemperatureLogic.snap(22f + units.adjustment(-1f, 2f), 2f, 10f, 40f))
            assertEquals(0f, units.adjustment(0f, 2f))
        }
    }

    @Test
    fun `Given no advertised step when using server units then Classic retains original increments`() {
        val celsius = ThermostatDisplayUnits("°C", ThermostatTemperatureUnit.DEFAULT)
        val fahrenheit = ThermostatDisplayUnits("°F", ThermostatTemperatureUnit.DEFAULT)
        assertEquals(0.5f, celsius.serverStep(null))
        assertEquals(1f, fahrenheit.serverStep(null))
        assertEquals(2f, celsius.serverStep(2f))
        assertEquals(0.5f, celsius.serverStep(0f))
        assertEquals(21.5f, ThermostatTemperatureLogic.snap(21f + celsius.serverStep(null), 0.5f, 7f, 35f))
        assertEquals(72f, ThermostatTemperatureLogic.snap(71f + fahrenheit.serverStep(null), 1f, 45f, 95f))
    }

    @Test
    fun `Given default or unknown source units when rendering then preserve original values and actions`() {
        val default = ThermostatDisplayUnits("°C", ThermostatTemperatureUnit.DEFAULT)
        val unknown = ThermostatDisplayUnits(null, ThermostatTemperatureUnit.FAHRENHEIT)
        assertFalse(default.hasOverride)
        assertFalse(unknown.hasOverride)
        assertEquals(21.5f, default.display(21.5f))
        assertEquals(21.5f, unknown.display(21.5f))
        assertEquals("°", unknown.symbol)
        assertEquals(2f, unknown.adjustment(1f, 2f))
        assertEquals("target|", default.actionPrefix("target|"))
        assertEquals("target|", unknown.actionPrefix("target|"))
        val fahrenheit = ThermostatDisplayUnits("°C", ThermostatTemperatureUnit.FAHRENHEIT)
        assertTrue(fahrenheit.hasOverride)
        assertEquals("target|°F:", fahrenheit.actionPrefix("target|"))
    }
}
