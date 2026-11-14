package io.homeassistant.companion.android.common.sensors

import android.content.Context
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.Sleep
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.SensorRegistration
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.AppVersion
import io.homeassistant.companion.android.database.sensor.Attribute
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.database.sensor.SensorDao
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class SensorUpdaterTargetedTest {
    private val basicSensor = SensorManager.BasicSensor(
        id = "sleep_confidence",
        type = "sensor",
        statelessIcon = Mdi.Sleep,
    )
    private val repository = mockk<SensorRepository>(relaxed = true)
    private val integration = mockk<IntegrationRepository>(relaxed = true)
    private val serverManager = mockk<ServerManager> {
        coEvery { integrationRepository(any()) } returns integration
    }
    private val owner = mockk<SensorManager>(relaxed = true) {
        coEvery { getAvailableSensors() } returns listOf(basicSensor)
    }
    private val unrelatedManager = mockk<SensorManager>(relaxed = true) {
        coEvery { getAvailableSensors() } returns emptyList()
    }
    private val updater = SensorUpdater(
        mockk<Context>(relaxed = true),
        serverManager,
        repository,
        AppVersion("1.0", 1),
        setOf(unrelatedManager, owner),
        SensorSettingsIntentProvider { _, _, _, _ -> null },
        mockk(relaxed = true),
    )

    @ParameterizedTest
    @CsvSource(
        "true, true, 0, mdi:sleep, 1",
        "true, true, 87, mdi:sleep, 0",
        "true, true, 87, mdi:walk, 1",
        "false, true, 0, mdi:sleep, 0",
        "true, false, 0, mdi:sleep, 0",
    )
    fun `Given targeted refresh when delivering then preserves enabled registered and changed filtering`(
        enabled: Boolean,
        registered: Boolean,
        lastSentState: String,
        lastSentIcon: String,
        sendCount: Int,
    ) = runTest {
        coEvery { integration.updateSensors(any()) } returns true
        val sensor = Sensor(
            id = basicSensor.id,
            serverId = 1,
            enabled = enabled,
            registered = registered,
            state = "87",
            stateType = "int",
            icon = "mdi:sleep",
            lastSentState = lastSentState,
            lastSentIcon = lastSentIcon,
        )
        // A disabled second server must not receive the targeted update.
        coEvery { repository.getFull(basicSensor.id) } returns mapOf(
            sensor to listOf(Attribute(basicSensor.id, "motion", "4", "int")),
            sensor.copy(serverId = 2, enabled = false) to emptyList(),
        )

        updater.updateSensor(basicSensor.id)

        coVerify(exactly = 1) { owner.requestSensorUpdate() }
        coVerify(exactly = 0) { unrelatedManager.requestSensorUpdate() }
        coVerify(exactly = 0) { unrelatedManager.requestSensorUpdate(any()) }
        coVerify(exactly = sendCount) { integration.updateSensors(any()) }
        coVerify(exactly = sendCount) {
            repository.updateLastSentStateAndIcon(basicSensor.id, 1, "87", "mdi:sleep")
        }
        coVerify(exactly = 0) { repository.updateLastSentStateAndIcon(any(), 2, any(), any()) }
        coVerify(exactly = 0) { integration.registerSensor(any()) }
        coVerify(exactly = 0) { repository.update(any()) }
        if (sendCount != 0) {
            val payload = slot<List<SensorRegistration<Any>>>()
            coVerify { integration.updateSensors(capture(payload)) }
            assertEquals(basicSensor.id, payload.captured.single().uniqueId)
            assertEquals(87, payload.captured.single().state)
            assertEquals(mapOf("motion" to 4), payload.captured.single().attributes)
        }
    }

    @ParameterizedTest
    @CsvSource("true, true", "true, false", "false, true")
    fun `Given a rejected targeted update when delivering then only that server is reset for normal refresh`(
        sensorStillExists: Boolean,
        registeredAfterResponse: Boolean,
    ) = runTest {
        val rejected = Sensor(
            id = basicSensor.id,
            serverId = 1,
            enabled = true,
            registered = true,
            state = "87",
            stateType = "int",
            icon = "mdi:sleep",
            lastSentState = "42",
            lastSentIcon = "mdi:walk",
        )
        val accepted = rejected.copy(serverId = 2)
        val acceptedIntegration = mockk<IntegrationRepository>(relaxed = true) {
            coEvery { updateSensors(any()) } returns true
        }
        val dao = mockk<SensorDao>(relaxed = true)
        val serverDao = mockk<ServerDao> {
            coEvery { getAll() } returns listOf(1, 2).map { serverId ->
                mockk<Server> { every { id } returns serverId }
            }
        }
        val realRepository = SensorRepositoryImpl(dao, serverDao, setOf(basicSensor))
        var persisted: Sensor? = rejected
        coEvery { dao.get(basicSensor.id, 1) } coAnswers { persisted }
        coEvery { dao.upsert(any()) } coAnswers { persisted = firstArg() }
        coEvery { integration.updateSensors(any()) } coAnswers {
            persisted = rejected.copy(registered = registeredAfterResponse).takeIf { sensorStillExists }
            if (!sensorStillExists) {
                // The real repository returns an unregistered default, not null, for a missing row.
                assertEquals(
                    Sensor(basicSensor.id, 1, enabled = basicSensor.enabledByDefault, state = ""),
                    realRepository.get(basicSensor.id, 1),
                )
            }
            false
        }
        coEvery { serverManager.integrationRepository(2) } returns acceptedIntegration
        coEvery { dao.getFull(basicSensor.id) } returns mapOf(
            rejected to emptyList(),
            accepted to emptyList(),
        )

        SensorUpdater(
            mockk<Context>(relaxed = true),
            serverManager,
            realRepository,
            AppVersion("1.0", 1),
            setOf(unrelatedManager, owner),
            SensorSettingsIntentProvider { _, _, _, _ -> null },
            mockk(relaxed = true),
        ).updateSensor(basicSensor.id)

        coVerify(exactly = 1) { integration.updateSensors(any()) }
        coVerify(exactly = 1) { acceptedIntegration.updateSensors(any()) }
        coVerify(exactly = if (sensorStillExists) 1 else 0) {
            dao.upsert(rejected.copy(registered = null, lastSentState = null, lastSentIcon = null))
        }
        coVerify(exactly = if (sensorStillExists) 1 else 0) { dao.upsert(any()) }
        assertEquals(
            rejected.copy(registered = null, lastSentState = null, lastSentIcon = null).takeIf { sensorStillExists },
            persisted,
        )
        coVerify(exactly = 0) { dao.updateLastSentStateAndIcon(any(), 1, any(), any()) }
        coVerify(exactly = 1) {
            dao.updateLastSentStateAndIcon(basicSensor.id, 2, "87", "mdi:sleep")
        }
        coVerify(exactly = 1) { owner.requestSensorUpdate() }
        coVerify(exactly = 0) { unrelatedManager.requestSensorUpdate() }
        coVerify(exactly = 0) { unrelatedManager.requestSensorUpdate(any()) }
        coVerify(exactly = 0) { integration.registerSensor(any()) }
        coVerify(exactly = 0) { acceptedIntegration.registerSensor(any()) }
    }
}
