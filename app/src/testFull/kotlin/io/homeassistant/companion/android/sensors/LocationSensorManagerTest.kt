package io.homeassistant.companion.android.sensors

import io.homeassistant.companion.android.common.sensors.SensorManager.BasicSensor.Setting
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LocationSensorManagerTest {

    @Test
    fun `Given background location sensor when inspected then its settings are declared`() {
        assertEquals(
            listOf(
                Setting.Options("location_send_as", "exact", entries = listOf("exact", "zone_only")),
                Setting.Number("location_minimum_accuracy", 200),
                Setting.Toggle("location_ham_enabled", default = false),
                Setting.Number("location_ham_update_interval", 5),
                Setting.BluetoothDevices("location_ham_only_bt_dev"),
                Setting.Zones("location_ham_only_enter_zone"),
                Setting.Toggle("location_ham_zone_bt_combined", default = false),
                Setting.Number("location_ham_trigger_range", 300),
            ),
            LocationSensorManager.backgroundLocation.settings,
        )
    }

    @Test
    fun `Given zone location sensor when inspected then minimum accuracy setting is declared`() {
        assertEquals(
            listOf(Setting.Number("location_minimum_accuracy", 200)),
            LocationSensorManager.zoneLocation.settings,
        )
    }

    @Test
    fun `Given accurate location sensor when inspected then its settings are declared`() {
        assertEquals(
            listOf(
                Setting.Number("location_minimum_accuracy", 200),
                Setting.Number("location_minimum_time_updates", 60000),
                Setting.Toggle("location_include_sensor_update", default = false),
            ),
            LocationSensorManager.singleAccurateLocation.settings,
        )
    }
}
