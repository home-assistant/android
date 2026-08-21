package io.homeassistant.companion.android.common.sensors

import android.content.Context
import android.content.pm.PackageManager
import dagger.Lazy
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.database.sensor.SensorSetting
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
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

    private fun SensorManager.BasicSensor.Setting.toSensorSetting(
        sensorId: String,
        value: String = defaultValue,
    ) = SensorSetting(
        sensorId = sensorId,
        name = name,
        value = value,
        valueType = type,
        enabled = enabledByDefault,
        entries = entries,
    )
}
