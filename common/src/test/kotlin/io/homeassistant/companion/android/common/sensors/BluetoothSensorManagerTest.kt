package io.homeassistant.companion.android.common.sensors

import android.content.Context
import android.content.pm.PackageManager
import dagger.Lazy
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.database.sensor.SensorSetting
import io.homeassistant.companion.android.database.sensor.SensorSettingType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val ANDROID_12_SDK = 31

class BluetoothSensorManagerTest {

    private val context = mockk<Context>(relaxed = true) {
        every { checkPermission(any(), any(), any()) } returns PackageManager.PERMISSION_DENIED
    }
    private val sensorRepository = mockk<SensorRepository>(relaxed = true)
    private val manager = BluetoothSensorManager(
        context,
        sensorRepository,
        mockk<ServerManager>(relaxed = true),
        mockk<Lazy<SensorUpdater>>(relaxed = true),
    )

    @Test
    fun `Given no Bluetooth UUID when updating twice then generated UUID is persisted once and reused`() = runTest {
        SdkVersion.sdkInt = ANDROID_12_SDK
        var persistedUuid = ""
        coEvery { sensorRepository.getSettings(BluetoothSensorManager.bleTransmitter.id) } answers {
            BluetoothSensorManager.bleTransmitter.settings.map { setting ->
                setting.toSensorSetting(
                    BluetoothSensorManager.bleTransmitter.id,
                    value = if (setting.name == BluetoothSensorManager.SETTING_BLE_ID1) {
                        persistedUuid
                    } else {
                        setting.defaultValue
                    },
                )
            }
        }
        coEvery { sensorRepository.getSettings(BluetoothSensorManager.beaconMonitor.id) } returns
            BluetoothSensorManager.beaconMonitor.settings.map {
                it.toSensorSetting(BluetoothSensorManager.beaconMonitor.id)
            }
        coEvery {
            sensorRepository.getOrInitializeSettingValue(
                BluetoothSensorManager.bleTransmitter.id,
                BluetoothSensorManager.SETTING_BLE_ID1,
                any(),
            )
        } answers {
            persistedUuid = thirdArg()
            persistedUuid
        }

        manager.requestSensorUpdate()
        manager.requestSensorUpdate()

        assertTrue(runCatching { java.util.UUID.fromString(persistedUuid) }.isSuccess)
        coVerify(exactly = 1) {
            sensorRepository.getOrInitializeSettingValue(
                BluetoothSensorManager.bleTransmitter.id,
                BluetoothSensorManager.SETTING_BLE_ID1,
                persistedUuid,
            )
        }
    }

    @Test
    fun `Given a stored number when reading it as a number then returns the stored value`() = runTest {
        storedSetting(BluetoothSensorManager.SETTING_BLE_MEASURED_POWER, "-70")

        assertEquals(
            -70,
            manager.getNumberSetting(
                BluetoothSensorManager.bleTransmitter,
                BluetoothSensorManager.SETTING_BLE_MEASURED_POWER,
            ),
        )
    }

    @Test
    fun `Given an unparsable number when reading it as a number then returns the declared default`() = runTest {
        storedSetting(BluetoothSensorManager.SETTING_BLE_MEASURED_POWER, "")

        assertEquals(
            BluetoothSensorManager.DEFAULT_MEASURED_POWER_AT_1M,
            manager.getNumberSetting(
                BluetoothSensorManager.bleTransmitter,
                BluetoothSensorManager.SETTING_BLE_MEASURED_POWER,
            ),
        )
    }

    @Test
    fun `Given a toggle setting when reading it as a number then fails fast`() = runTest {
        var throwableCaptured: Throwable? = null
        FailFast.setHandler { throwable, _ -> throwableCaptured = throwable }
        storedSetting(BluetoothSensorManager.SETTING_BLE_HOME_WIFI_ONLY, "true")

        val result = manager.getNumberSetting(
            BluetoothSensorManager.bleTransmitter,
            BluetoothSensorManager.SETTING_BLE_HOME_WIFI_ONLY,
        )

        assertEquals(0, result)
        assertNotNull(throwableCaptured)
    }

    @Test
    fun `Given the beacon monitor when inspected then the RSSI multiplier is its only decimal setting`() {
        val decimals = BluetoothSensorManager.beaconMonitor.settings
            .filterIsInstance<SensorManager.BasicSensor.Setting.Decimal>()

        assertEquals(1, decimals.size)
        assertEquals(1.05, decimals.single().default)
        assertEquals(SensorSettingType.NUMBER, decimals.single().type)
    }

    private fun storedSetting(name: String, value: String) {
        coEvery { sensorRepository.getSettings(BluetoothSensorManager.bleTransmitter.id) } returns
            BluetoothSensorManager.bleTransmitter.settings.map {
                it.toSensorSetting(
                    BluetoothSensorManager.bleTransmitter.id,
                    value = if (it.name == name) value else it.defaultValue,
                )
            }
    }

    private fun SensorManager.BasicSensor.Setting.toSensorSetting(
        sensorId: String,
        value: String = defaultValue,
    ) = SensorSetting(
        sensorId = sensorId,
        name = name,
        value = value,
        valueType = type,
        enabled = enabledByDefault,
        entries = if (this is SensorManager.BasicSensor.Setting.Options) entries else emptyList(),
    )
}
