package io.homeassistant.companion.android.sensors.healthconnect.command

import io.homeassistant.companion.android.sensors.healthconnect.HealthConnectDataType
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ConsoleLogExtension::class)
class HealthConnectUnitConversionTest {

    @Test
    fun `null or blank unit passes value through unchanged`() {
        val v = HealthConnectUnitConversion.toCanonical(HealthConnectDataType.Weight, 75.2, null)
        assertEquals(75.2, v, 0.0001)
        assertEquals(75.2, HealthConnectUnitConversion.toCanonical(HealthConnectDataType.Weight, 75.2, ""), 0.0001)
        assertEquals(75.2, HealthConnectUnitConversion.toCanonical(HealthConnectDataType.Weight, 75.2, "  "), 0.0001)
    }

    @Test
    fun `pounds convert to kilograms for Weight`() {
        val v = HealthConnectUnitConversion.toCanonical(HealthConnectDataType.Weight, 165.0, "lb")
        assertEquals(74.84, v, 0.01)
    }

    @Test
    fun `grams convert to kilograms for mass types`() {
        val v = HealthConnectUnitConversion.toCanonical(HealthConnectDataType.BoneMass, 2500.0, "g")
        assertEquals(2.5, v, 0.0001)
    }

    @Test
    fun `feet convert to meters for Height`() {
        val v = HealthConnectUnitConversion.toCanonical(HealthConnectDataType.Height, 6.0, "ft")
        assertEquals(1.8288, v, 0.0001)
    }

    @Test
    fun `centimeters convert to meters for Height`() {
        val v = HealthConnectUnitConversion.toCanonical(HealthConnectDataType.Height, 180.0, "cm")
        assertEquals(1.80, v, 0.0001)
    }

    @Test
    fun `kilometers convert to meters for Distance`() {
        val v = HealthConnectUnitConversion.toCanonical(HealthConnectDataType.Distance, 5.0, "km")
        assertEquals(5000.0, v, 0.0001)
    }

    @Test
    fun `fahrenheit converts to celsius for BodyTemperature`() {
        val v = HealthConnectUnitConversion.toCanonical(HealthConnectDataType.BodyTemperature, 98.6, "F")
        assertEquals(37.0, v, 0.01)
    }

    @Test
    fun `mg per dL converts to mmol per L for BloodGlucose`() {
        val v = HealthConnectUnitConversion.toCanonical(HealthConnectDataType.BloodGlucose, 100.0, "mg/dL")
        assertEquals(5.55, v, 0.01)
    }

    @Test
    fun `milliliters convert to liters for Hydration`() {
        val v = HealthConnectUnitConversion.toCanonical(HealthConnectDataType.Hydration, 500.0, "mL")
        assertEquals(0.5, v, 0.0001)
    }

    @Test
    fun `joules convert to kilocalories for energy`() {
        val v = HealthConnectUnitConversion.toCanonical(HealthConnectDataType.ActiveCaloriesBurned, 4184.0, "J")
        assertEquals(1.0, v, 0.0001)
    }

    @Test
    fun `fraction converts to percent for OxygenSaturation`() {
        val v = HealthConnectUnitConversion.toCanonical(HealthConnectDataType.OxygenSaturation, 0.97, "fraction")
        assertEquals(97.0, v, 0.01)
    }

    @Test
    fun `unit string is case insensitive`() {
        val a = HealthConnectUnitConversion.toCanonical(HealthConnectDataType.Weight, 165.0, "LB")
        val b = HealthConnectUnitConversion.toCanonical(HealthConnectDataType.Weight, 165.0, "lb")
        assertEquals(a, b, 0.0001)
    }

    @Test
    fun `unknown unit throws InvalidPayloadException`() {
        assertThrows(HealthConnectWriteCommandPayload.InvalidPayloadException::class.java) {
            HealthConnectUnitConversion.toCanonical(HealthConnectDataType.Weight, 1.0, "stones")
        }
    }

    @Test
    fun `data type without unit support rejects any unit`() {
        assertThrows(HealthConnectWriteCommandPayload.InvalidPayloadException::class.java) {
            HealthConnectUnitConversion.toCanonical(HealthConnectDataType.Steps, 1000.0, "kilosteps")
        }
    }
}
