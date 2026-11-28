package io.homeassistant.companion.android.database.wear

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ThermostatPreferenceConvertersTest {
    private val converters = ThermostatPreferenceConverters()

    @Test
    fun `Given existing layout values when converted then preserve stored strings`() {
        assertEquals(ThermostatLayoutStyle.CLASSIC, converters.toLayoutStyle("classic"))
        assertEquals(ThermostatLayoutStyle.ENHANCED, converters.toLayoutStyle("enhanced"))
        assertEquals("classic", converters.fromLayoutStyle(ThermostatLayoutStyle.CLASSIC))
        assertEquals("enhanced", converters.fromLayoutStyle(ThermostatLayoutStyle.ENHANCED))
        assertEquals(ThermostatLayoutStyle.CLASSIC, converters.toLayoutStyle("unknown"))
    }

    @Test
    fun `Given existing temperature units when converted then preserve stored strings`() {
        assertEquals(ThermostatTemperatureUnit.DEFAULT, converters.toTemperatureUnit("default"))
        assertEquals(ThermostatTemperatureUnit.CELSIUS, converters.toTemperatureUnit("C"))
        assertEquals(ThermostatTemperatureUnit.FAHRENHEIT, converters.toTemperatureUnit("F"))
        assertEquals("default", converters.fromTemperatureUnit(ThermostatTemperatureUnit.DEFAULT))
        assertEquals("C", converters.fromTemperatureUnit(ThermostatTemperatureUnit.CELSIUS))
        assertEquals("F", converters.fromTemperatureUnit(ThermostatTemperatureUnit.FAHRENHEIT))
        assertEquals(ThermostatTemperatureUnit.DEFAULT, converters.toTemperatureUnit("unknown"))
        assertEquals(ThermostatTemperatureUnit.CELSIUS, converters.toTemperatureUnit("°C"))
        assertEquals(ThermostatTemperatureUnit.FAHRENHEIT, converters.toTemperatureUnit("°F"))
    }
}
