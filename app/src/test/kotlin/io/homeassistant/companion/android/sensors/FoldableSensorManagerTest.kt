package io.homeassistant.companion.android.sensors

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowLayoutInfo
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.BookOpenBlankVariant
import io.github.timoptr.mdiicons.generated.Cellphone
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.sensors.SensorRepository
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.common.util.mdiName
import io.homeassistant.companion.android.database.sensor.Sensor
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FoldableSensorManagerTest {

    private val sensorId = FoldableSensorManager.foldableScreen.id
    private val context = mockk<Context>()
    private val packageManager = mockk<PackageManager>()
    private val sensorRepository = mockk<SensorRepository>()
    private val serverManager = mockk<ServerManager>()
    private val openLayout = WindowLayoutInfo(listOf(mockk<FoldingFeature>()))
    private val closedLayout = WindowLayoutInfo(emptyList())

    @BeforeEach
    fun setUp() {
        SdkVersion.sdkInt = Build.VERSION_CODES.R
        every { context.packageManager } returns packageManager
        setHasHingeAngleSensor(true)
        setSensorEnabled(true)
        coJustRun { sensorRepository.update(any()) }
        coJustRun { sensorRepository.replaceAllAttributes(any(), any()) }
        mockkObject(SensorReceiver.Companion)
        every { SensorReceiver.updateSensor(any(), any()) } just runs
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `Given SDK is lower than Android 11 then sensor manager is absent`() {
        SdkVersion.sdkInt = Build.VERSION_CODES.Q

        assertFalse(createManager().hasSensor())
    }

    @Test
    fun `Given SDK is at least Android 11 then sensor manager is present only with a hinge angle sensor`() {
        setHasHingeAngleSensor(false)
        assertFalse(createManager().hasSensor())

        setHasHingeAngleSensor(true)
        assertTrue(createManager().hasSensor())
    }

    @Test
    fun `Given a fold in the window layout when request update then screen is open`() = runTest {
        createManager(flowOf(openLayout)).requestSensorUpdate()

        val sensor = updatedSensor()
        assertEquals("true", sensor.state)
        assertEquals(Mdi.BookOpenBlankVariant.mdiName, sensor.icon)
    }

    @Test
    fun `Given no fold in the window layout when request update then screen is closed`() = runTest {
        createManager(flowOf(closedLayout)).requestSensorUpdate()

        val sensor = updatedSensor()
        assertEquals("false", sensor.state)
        assertEquals(Mdi.Cellphone.mdiName, sensor.icon)
    }

    @Test
    fun `Given disabled sensor when request update then does not update state`() = runTest {
        setSensorEnabled(false)

        createManager(flowOf(openLayout)).requestSensorUpdate()

        coVerify(exactly = 0) { sensorRepository.update(any()) }
    }

    @Test
    fun `Given window layout is never reported when request update then gives up without updating state`() = runTest {
        createManager(flow { awaitCancellation() }).requestSensorUpdate()

        coVerify(exactly = 0) { sensorRepository.update(any()) }
    }

    @Test
    fun `Given window layout is unavailable when request update then does not update state`() = runTest {
        createManager(emptyFlow()).requestSensorUpdate()

        coVerify(exactly = 0) { sensorRepository.update(any()) }
    }

    @Test
    fun `Given enabled sensor when fold state changes then requests a sensor update for each change`() = runTest {
        createManager(flowOf(openLayout, openLayout, closedLayout)).updateOnFoldChange()

        verify(exactly = 2) { SensorReceiver.updateSensor(context, sensorId) }
    }

    @Test
    fun `Given disabled sensor when fold state changes then does not request a sensor update`() = runTest {
        setSensorEnabled(false)

        createManager(flowOf(openLayout, closedLayout)).updateOnFoldChange()

        verify(exactly = 0) { SensorReceiver.updateSensor(any(), any()) }
    }

    @Test
    fun `Given device is not a foldable when observing fold changes then returns without observing`() = runTest {
        setHasHingeAngleSensor(false)

        createManager(flow { awaitCancellation() }).updateOnFoldChange()

        verify(exactly = 0) { SensorReceiver.updateSensor(any(), any()) }
    }

    private fun createManager(windowLayoutInfo: Flow<WindowLayoutInfo> = emptyFlow()) = FoldableSensorManager(
        applicationContext = context,
        sensorRepository = sensorRepository,
        serverManager = serverManager,
        windowLayoutInfo = { windowLayoutInfo },
    )

    private fun setHasHingeAngleSensor(hasSensor: Boolean) {
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE) } returns hasSensor
    }

    private fun setSensorEnabled(enabled: Boolean) {
        coEvery { sensorRepository.get(sensorId) } returns listOf(
            Sensor(id = sensorId, serverId = 1, enabled = enabled, state = ""),
        )
    }

    private fun updatedSensor(): Sensor {
        val updated = slot<Sensor>()
        coVerify { sensorRepository.update(capture(updated)) }
        return updated.captured
    }
}
