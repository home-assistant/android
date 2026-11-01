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
import io.mockk.coEvery
import io.mockk.coVerify
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
        if (sendCount != 0) {
            val payload = slot<List<SensorRegistration<Any>>>()
            coVerify { integration.updateSensors(capture(payload)) }
            assertEquals(basicSensor.id, payload.captured.single().uniqueId)
            assertEquals(87, payload.captured.single().state)
            assertEquals(mapOf("motion" to 4), payload.captured.single().attributes)
        }
    }
}
