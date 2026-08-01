package io.homeassistant.companion.android.common.sensors

import app.cash.turbine.test
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.database.sensor.Attribute
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.database.sensor.SensorDao
import io.homeassistant.companion.android.database.sensor.SensorSetting
import io.homeassistant.companion.android.database.sensor.SensorSettingType
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SensorRepositoryImplTest {

    private val dao: SensorDao = mockk(relaxed = true)
    private val serverDao: ServerDao = mockk(relaxed = true)

    private val basicSensors = setOf(
        SensorManager.BasicSensor(
            id = "last_update",
            type = "sensor",
            enabledByDefault = true,
            settings = listOf(
                SensorManager.BasicSensor.Setting(
                    name = "add_new_intent",
                    type = SensorSettingType.TOGGLE,
                    defaultValue = "false",
                ),
                SensorManager.BasicSensor.Setting(
                    name = "intent_1",
                    type = SensorSettingType.STRING,
                    defaultValue = "",
                    enabledByDefault = false,
                ),
            ),
        ),
        SensorManager.BasicSensor(id = "app_inactive", type = "sensor", enabledByDefault = false),
    )
    private val repository: SensorRepository = SensorRepositoryImpl(dao, serverDao, basicSensors)

    private fun serverMock(id: Int): Server = mockk<Server>(relaxed = true).also { every { it.id } returns id }

    @Test
    fun `Given a stored row when get by id and server then returns the row without writing`() = runTest {
        val stored = Sensor("last_update", 1, enabled = false, state = "on")
        coEvery { dao.get("last_update", 1) } returns stored

        val result = repository.get("last_update", 1)

        // Stored row wins even though the basic sensor marks last_update enabled-by-default.
        assertEquals(stored, result)
        coVerify(exactly = 0) { dao.upsert(any()) }
    }

    @Test
    fun `Given no row when get by id and server then returns basic sensor default without writing`() = runTest {
        coEvery { dao.get("last_update", 1) } returns null

        val result = repository.get("last_update", 1)

        assertEquals(Sensor("last_update", 1, enabled = true, state = ""), result)
        coVerify(exactly = 0) { dao.upsert(any()) }
    }

    @Test
    fun `Given no row for a disabled-by-default sensor when get by id and server then default is disabled`() = runTest {
        coEvery { dao.get("app_inactive", 1) } returns null

        val result = repository.get("app_inactive", 1)

        assertEquals(Sensor("app_inactive", 1, enabled = false, state = ""), result)
    }

    @Test
    fun `Given a sensor absent from the basic sensor set and no row when get then fails fast and returns null`() = runTest {
        coEvery { dao.get("unknown", 1) } returns null
        var throwableCaptured: Throwable? = null
        FailFast.setHandler { throwable, _ -> throwableCaptured = throwable }

        val result = repository.get("unknown", 1)

        // No backing BasicSensor means the sensor doesn't exist: FailFast fires and the read is null.
        assertNotNull(throwableCaptured)
        assertNull(result)
    }

    @Test
    fun `Given get by id then configured servers without a row get a basic sensor default`() = runTest {
        coEvery { serverDao.getAll() } returns listOf(serverMock(1), serverMock(2))
        coEvery { dao.get("last_update") } returns listOf(Sensor("last_update", 1, enabled = false, state = "on"))

        val result = repository.get("last_update").toSet()

        assertEquals(
            setOf(
                Sensor("last_update", 1, enabled = false, state = "on"),
                Sensor("last_update", 2, enabled = true, state = ""),
            ),
            result,
        )
    }

    @Test
    fun `Given no row when getFull by server then returns basic sensor default with empty attributes`() = runTest {
        coEvery { dao.getFull("last_update", 1) } returns emptyMap()

        val result = repository.getFull("last_update", 1)

        assertEquals(mapOf(Sensor("last_update", 1, enabled = true, state = "") to emptyList<Attribute>()), result)
    }

    @Test
    fun `Given a stored row when getFull by server then returns it with its attributes`() = runTest {
        val stored = Sensor("last_update", 1, enabled = false, state = "on")
        val storedAttrs = listOf(Attribute("last_update", "k", "v", "string"))
        coEvery { dao.getFull("last_update", 1) } returns mapOf(stored to storedAttrs)

        val result = repository.getFull("last_update", 1)

        assertEquals(mapOf(stored to storedAttrs), result)
    }

    @Test
    fun `Given getFull by id then configured servers without a row get a basic sensor default with no attributes`() = runTest {
        coEvery { serverDao.getAll() } returns listOf(serverMock(1), serverMock(2))
        val stored = Sensor("last_update", 1, enabled = false, state = "on")
        val storedAttrs = listOf(Attribute("last_update", "k", "v", "string"))
        coEvery { dao.getFull("last_update") } returns mapOf(stored to storedAttrs)

        val result = repository.getFull("last_update")

        // Server 1 keeps its stored row and attributes; server 2 (no row) is an empty default.
        assertEquals(
            setOf(stored, Sensor("last_update", 2, enabled = true, state = "")),
            result.keys,
        )
        val byServer = result.mapKeys { it.key.serverId }
        assertEquals(storedAttrs, byServer[1])
        assertEquals(emptyList<Attribute>(), byServer[2])
    }

    @Test
    fun `Given getFullFlow then configured servers without a row get a basic sensor default`() = runTest {
        every { serverDao.getAllFlow() } returns flowOf(listOf(serverMock(1), serverMock(2)))
        val stored = Sensor("last_update", 1, enabled = false, state = "on")
        val storedAttrs = listOf(Attribute("last_update", "k", "v", "string"))
        every { dao.getFullFlow("last_update") } returns flowOf(mapOf(stored to storedAttrs))

        repository.getFullFlow("last_update").test {
            val emitted = awaitItem()
            // Server 1 keeps its stored row (with attributes); server 2 (no row) is an empty default.
            assertEquals(
                setOf(stored, Sensor("last_update", 2, enabled = true, state = "")),
                emitted.keys,
            )
            val byServer = emitted.mapKeys { it.key.serverId }
            assertEquals(storedAttrs, byServer[1])
            assertEquals(emptyList<Attribute>(), byServer[2])
            awaitComplete()
        }
    }

    @Test
    fun `Given removeSensorsExceptServers then deletes every orphaned row`() = runTest {
        val orphans = listOf(
            Sensor("last_update", 9, enabled = true, state = ""),
            Sensor("app_inactive", 9, enabled = false, state = ""),
        )
        coEvery { dao.getAllExceptServer(listOf(1)) } returns orphans

        repository.removeSensorsExceptServers(listOf(1))

        coVerify { dao.removeSensor("last_update", 9) }
        coVerify { dao.removeSensor("app_inactive", 9) }
    }

    @Test
    fun `Given getAllFlow then emits every basic sensor per configured server, stored or defaulted`() = runTest {
        every { dao.getAllFlow() } returns flowOf(listOf(Sensor("last_update", 1, enabled = false, state = "on")))
        every { serverDao.getAllFlow() } returns flowOf(listOf(serverMock(1), serverMock(2)))

        repository.getAllFlow().test {
            val emitted = awaitItem().associateBy { it.id to it.serverId }
            // Stored row preserved...
            assertEquals(Sensor("last_update", 1, enabled = false, state = "on"), emitted["last_update" to 1])
            // ...and defaults filled in for the other basic sensor/server combinations.
            assertEquals(Sensor("last_update", 2, enabled = true, state = ""), emitted["last_update" to 2])
            assertEquals(Sensor("app_inactive", 1, enabled = false, state = ""), emitted["app_inactive" to 1])
            assertEquals(Sensor("app_inactive", 2, enabled = false, state = ""), emitted["app_inactive" to 2])
            awaitComplete()
        }
    }

    @Test
    fun `Given setSensorEnabled when called then delegates to dao`() = runTest {
        repository.setSensorEnabled("last_update", listOf(1, 2), enabled = true)

        coVerify { dao.setSensorEnabled("last_update", listOf(1, 2), true) }
    }

    @Test
    fun `Given setSensorsEnabled when called then delegates to dao`() = runTest {
        repository.setSensorsEnabled(listOf("last_update", "app_inactive"), serverId = 1, enabled = false)

        coVerify { dao.setSensorsEnabled(listOf("last_update", "app_inactive"), 1, false) }
    }

    @Test
    fun `Given update when called then upserts the row in a single call`() = runTest {
        val sensor = Sensor("last_update", 1, enabled = true, state = "on")

        repository.update(sensor)

        coVerify(exactly = 1) { dao.upsert(sensor) } // single insert-or-update
    }

    @Test
    fun `Given removeServer when called then delegates to dao`() = runTest {
        repository.removeServer(serverId = 2)

        coVerify { dao.removeServer(2) }
    }

    @Test
    fun `Given getEnabledCount then counts effective-enabled across servers including basic sensor defaults`() = runTest {
        coEvery { serverDao.getAll() } returns listOf(serverMock(1), serverMock(2))
        // Server 1 has no rows; server 2 has app_inactive explicitly enabled.
        coEvery { dao.getAll() } returns listOf(Sensor("app_inactive", 2, enabled = true, state = ""))

        val result = repository.getEnabledCount()

        // last_update is enabled-by-default on both servers (2), plus app_inactive enabled on server 2 (1).
        assertEquals(3, result)
    }

    @Test
    fun `Given a row disabling a default-enabled sensor when getEnabledCount then it is not counted`() = runTest {
        coEvery { serverDao.getAll() } returns listOf(serverMock(1), serverMock(2))
        // last_update is enabled-by-default but explicitly disabled on server 1.
        coEvery { dao.getAll() } returns listOf(Sensor("last_update", 1, enabled = false, state = ""))

        val result = repository.getEnabledCount()

        // last_update counts only on server 2 (1); app_inactive is disabled-by-default everywhere.
        assertEquals(1, result)
    }

    @Test
    fun `Given rows for removed servers or unknown sensors when getEnabledCount then they are ignored`() = runTest {
        coEvery { serverDao.getAll() } returns listOf(serverMock(1))
        coEvery { dao.getAll() } returns listOf(
            // Orphan row for a server that is no longer configured.
            Sensor("app_inactive", 99, enabled = true, state = ""),
            // Row for a sensor absent from the basic sensor.
            Sensor("unknown", 1, enabled = true, state = ""),
        )

        val result = repository.getEnabledCount()

        // Only last_update's default-enabled state on the single configured server counts.
        assertEquals(1, result)
    }

    @Test
    fun `Given no stored settings when getSettings then returns declarations without writing`() = runTest {
        coEvery { dao.getSettings("last_update") } returns emptyList()

        val result = repository.getSettings("last_update")

        assertEquals(
            listOf(
                SensorSetting("last_update", "add_new_intent", "false", SensorSettingType.TOGGLE),
                SensorSetting(
                    "last_update",
                    "intent_1",
                    "",
                    SensorSettingType.STRING,
                    enabled = false,
                ),
            ),
            result,
        )
        coVerify(exactly = 0) { dao.add(any<SensorSetting>()) }
    }

    @Test
    fun `Given stored overrides and dynamic settings when getSettings then uses declaration metadata and order`() = runTest {
        val storedOverride = SensorSetting(
            "last_update",
            "intent_1",
            "override",
            SensorSettingType.NUMBER,
            enabled = true,
            entries = listOf("stale"),
        )
        val dynamic = SensorSetting("last_update", "intent_2", "dynamic", SensorSettingType.STRING)
        coEvery { dao.getSettings("last_update") } returns listOf(dynamic, storedOverride)

        val result = repository.getSettings("last_update")

        assertEquals(
            listOf(
                SensorSetting("last_update", "add_new_intent", "false", SensorSettingType.TOGGLE),
                SensorSetting(
                    "last_update",
                    "intent_1",
                    "override",
                    SensorSettingType.STRING,
                    enabled = true,
                ),
                dynamic,
            ),
            result,
        )
    }

    @Test
    fun `Given empty stored flow when getSettingsFlow then emits declared settings`() = runTest {
        every { dao.getSettingsFlow("last_update") } returns flowOf(emptyList())

        repository.getSettingsFlow("last_update").test {
            assertEquals(2, awaitItem().size)
            awaitComplete()
        }
    }

    @Test
    fun `Given declaration-only setting when updating value then persists changed effective setting`() = runTest {
        coEvery { dao.getSettings("last_update") } returns emptyList()

        repository.updateSettingValue("last_update", "add_new_intent", "true")

        coVerify {
            dao.upsertSettingValue(
                SensorSetting("last_update", "add_new_intent", "false", SensorSettingType.TOGGLE),
                "true",
            )
        }
    }

    @Test
    fun `Given concurrent explicit value when initializing setting then returns explicit value`() = runTest {
        coEvery { dao.getSettings("last_update") } returns emptyList()
        coEvery {
            dao.getOrInitializeSettingValue(
                SensorSetting("last_update", "intent_1", "", SensorSettingType.STRING, enabled = false),
                "generated",
            )
        } returns "explicit"

        val result = repository.getOrInitializeSettingValue("last_update", "intent_1", "generated")

        assertEquals("explicit", result)
    }

    @Test
    fun `Given declaration-only list setting when updating value then preserves declaration metadata`() = runTest {
        val listSetting = SensorManager.BasicSensor.Setting(
            name = "list_setting",
            type = SensorSettingType.LIST,
            defaultValue = "first",
            enabledByDefault = false,
            entries = listOf("first", "second"),
        )
        val repository = SensorRepositoryImpl(
            dao,
            serverDao,
            setOf(SensorManager.BasicSensor("list_sensor", "sensor", settings = listOf(listSetting))),
        )
        coEvery { dao.getSettings("list_sensor") } returns emptyList()

        repository.updateSettingValue("list_sensor", "list_setting", "second")

        coVerify {
            dao.upsertSettingValue(
                SensorSetting(
                    sensorId = "list_sensor",
                    name = "list_setting",
                    value = "first",
                    valueType = SensorSettingType.LIST,
                    enabled = false,
                    entries = listOf("first", "second"),
                ),
                "second",
            )
        }
    }

    @Test
    fun `Given declaration-only disabled setting when enabling then persists changed effective setting`() = runTest {
        coEvery { dao.getSettings("last_update") } returns emptyList()

        repository.updateSettingEnabled("last_update", "intent_1", true)

        coVerify {
            dao.upsertSettingEnabled(
                SensorSetting(
                    "last_update",
                    "intent_1",
                    "",
                    SensorSettingType.STRING,
                    enabled = false,
                ),
                true,
            )
        }
    }

    @Test
    fun `Given stored dynamic setting when updating enabled then persists changed setting`() = runTest {
        val dynamic = SensorSetting("last_update", "intent_2", "dynamic", SensorSettingType.STRING)
        coEvery { dao.getSettings("last_update") } returns listOf(dynamic)

        repository.updateSettingEnabled("last_update", "intent_2", false)

        coVerify { dao.upsertSettingEnabled(dynamic, false) }
    }
}
