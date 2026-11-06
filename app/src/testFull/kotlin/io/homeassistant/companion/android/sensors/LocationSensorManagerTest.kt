package io.homeassistant.companion.android.sensors

import io.homeassistant.companion.android.common.data.integration.UpdateLocation
import io.homeassistant.companion.android.common.sensors.SensorManager.BasicSensor.Setting
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTime::class)
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

    @Test
    fun `Given same coordinates with a newer fix time when building the duplicate key then the keys differ`() {
        val first = exactUpdate(locationTime = Instant.fromEpochMilliseconds(1_790_426_575_000))
        val newer = exactUpdate(locationTime = Instant.fromEpochMilliseconds(1_790_426_580_000))

        assertNotEquals(first.duplicateKey(), newer.duplicateKey())
    }

    @Test
    fun `Given the same fix delivered twice when building the duplicate key then the keys are equal`() {
        val fixTime = Instant.fromEpochMilliseconds(1_790_426_575_000)

        assertEquals(exactUpdate(locationTime = fixTime).duplicateKey(), exactUpdate(locationTime = fixTime).duplicateKey())
    }

    @Test
    fun `Given no fix time when building the duplicate key then only the coordinates are used`() {
        assertEquals("[1.0, 2.0]", exactUpdate(locationTime = null).duplicateKey())
    }

    private fun exactUpdate(locationTime: Instant?) = UpdateLocation(
        gps = listOf(1.0, 2.0),
        gpsAccuracy = 5,
        locationName = null,
        inZones = null,
        speed = 0,
        altitude = 0,
        course = 0,
        verticalAccuracy = 0,
        locationTime = locationTime,
    )
}
