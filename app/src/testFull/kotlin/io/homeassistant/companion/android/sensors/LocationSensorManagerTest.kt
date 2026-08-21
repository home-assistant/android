package io.homeassistant.companion.android.sensors

import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.database.sensor.SensorSettingType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LocationSensorManagerTest {

    @Test
    fun `Given background location sensor when inspected then its settings are declared`() {
        assertEquals(
            listOf(
                setting(
                    "location_send_as",
                    SensorSettingType.LIST,
                    "exact",
                    entries = listOf("exact", "zone_only"),
                ),
                setting("location_minimum_accuracy", SensorSettingType.NUMBER, "200"),
                setting("location_ham_enabled", SensorSettingType.TOGGLE, "false"),
                setting("location_ham_update_interval", SensorSettingType.NUMBER, "5"),
                setting("location_ham_only_bt_dev", SensorSettingType.LIST_BLUETOOTH, ""),
                setting("location_ham_only_enter_zone", SensorSettingType.LIST_ZONES, ""),
                setting("location_ham_zone_bt_combined", SensorSettingType.TOGGLE, "false"),
                setting("location_ham_trigger_range", SensorSettingType.NUMBER, "300"),
            ),
            LocationSensorManager.backgroundLocation.settings,
        )
    }

    @Test
    fun `Given zone location sensor when inspected then minimum accuracy is declared`() {
        assertEquals(
            listOf(setting("location_minimum_accuracy", SensorSettingType.NUMBER, "200")),
            LocationSensorManager.zoneLocation.settings,
        )
    }

    @Test
    fun `Given accurate location sensor when inspected then its settings are declared`() {
        assertEquals(
            listOf(
                setting("location_minimum_accuracy", SensorSettingType.NUMBER, "200"),
                setting("location_minimum_time_updates", SensorSettingType.NUMBER, "60000"),
                setting("location_include_sensor_update", SensorSettingType.TOGGLE, "false"),
            ),
            LocationSensorManager.singleAccurateLocation.settings,
        )
    }

    private fun setting(
        name: String,
        type: SensorSettingType,
        defaultValue: String,
        entries: List<String> = emptyList(),
    ) = SensorManager.BasicSensor.Setting(name, type, defaultValue, entries = entries)
}
