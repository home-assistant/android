package io.homeassistant.companion.android.database.sensor

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifySequence
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SensorDaoTest {

    private val dao = mockk<SensorDao>()

    @Test
    fun `Given stored setting when updating value then only updates value column`() = runTest {
        val setting = SensorSetting("sensor", "setting", "default", SensorSettingType.STRING, enabled = false)
        coEvery { dao.upsertSettingValue(setting, "changed") } answers { callOriginal() }
        coEvery { dao.updateSettingValue("sensor", "setting", "changed") } returns 1

        dao.upsertSettingValue(setting, "changed")

        coVerifySequence {
            dao.upsertSettingValue(setting, "changed")
            dao.updateSettingValue("sensor", "setting", "changed")
        }
        coVerify(exactly = 0) { dao.add(any<SensorSetting>()) }
    }

    @Test
    fun `Given stored setting when updating enabled then only updates enabled column`() = runTest {
        val setting = SensorSetting("sensor", "setting", "value", SensorSettingType.STRING, enabled = false)
        coEvery { dao.upsertSettingEnabled(setting, true) } answers { callOriginal() }
        coEvery { dao.updateSettingEnabled("sensor", "setting", true) } returns 1

        dao.upsertSettingEnabled(setting, true)

        coVerifySequence {
            dao.upsertSettingEnabled(setting, true)
            dao.updateSettingEnabled("sensor", "setting", true)
        }
        coVerify(exactly = 0) { dao.add(any<SensorSetting>()) }
    }

    @Test
    fun `Given missing setting when updating enabled then inserts setting with declaration metadata`() = runTest {
        val setting = SensorSetting(
            sensorId = "sensor",
            name = "setting",
            value = "default",
            valueType = SensorSettingType.LIST,
            enabled = false,
            entries = listOf("default", "other"),
        )
        coEvery { dao.upsertSettingEnabled(setting, true) } answers { callOriginal() }
        coEvery { dao.updateSettingEnabled("sensor", "setting", true) } returns 0
        coEvery { dao.add(any<SensorSetting>()) } returns Unit

        dao.upsertSettingEnabled(setting, true)

        coVerifySequence {
            dao.upsertSettingEnabled(setting, true)
            dao.updateSettingEnabled("sensor", "setting", true)
            dao.add(setting.copy(enabled = true))
        }
    }

    @Test
    fun `Given explicit value when initializing setting then returns explicit value without changing it`() = runTest {
        val setting = SensorSetting("sensor", "setting", "", SensorSettingType.STRING, enabled = false)
        coEvery { dao.getOrInitializeSettingValue(setting, "generated") } answers { callOriginal() }
        coEvery { dao.getSettingValue("sensor", "setting") } returns "explicit"

        val result = dao.getOrInitializeSettingValue(setting, "generated")

        assertEquals("explicit", result)
        coVerify(exactly = 0) { dao.updateSettingValue(any(), any(), any()) }
        coVerify(exactly = 0) { dao.add(any<SensorSetting>()) }
    }

    @Test
    fun `Given missing value when initializing setting then inserts and returns initial value`() = runTest {
        val setting = SensorSetting("sensor", "setting", "", SensorSettingType.STRING, enabled = false)
        coEvery { dao.getOrInitializeSettingValue(setting, "generated") } answers { callOriginal() }
        coEvery { dao.getSettingValue("sensor", "setting") } returns null
        coEvery { dao.add(any<SensorSetting>()) } returns Unit

        val result = dao.getOrInitializeSettingValue(setting, "generated")

        assertEquals("generated", result)
        coVerify { dao.add(setting.copy(value = "generated")) }
    }

    @Test
    fun `Given empty value when initializing setting then updates and returns initial value`() = runTest {
        val setting = SensorSetting("sensor", "setting", "", SensorSettingType.STRING, enabled = false)
        coEvery { dao.getOrInitializeSettingValue(setting, "generated") } answers { callOriginal() }
        coEvery { dao.getSettingValue("sensor", "setting") } returns ""
        coEvery { dao.updateSettingValue("sensor", "setting", "generated") } returns 1

        val result = dao.getOrInitializeSettingValue(setting, "generated")

        assertEquals("generated", result)
        coVerify { dao.updateSettingValue("sensor", "setting", "generated") }
        coVerify(exactly = 0) { dao.add(any<SensorSetting>()) }
    }
}
