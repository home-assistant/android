package io.homeassistant.companion.android.common.sensors

import android.content.Context
import android.content.Context.AUDIO_SERVICE
import android.media.AudioManager
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.sensor.Attribute
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.database.sensor.SensorDao
import io.homeassistant.companion.android.testing.unit.ConsoleLogRule
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [28]) // Build.VERSION_CODES.P
class AudioSensorManagerTest {

    @get:Rule
    val consoleLogRule = ConsoleLogRule()

    private lateinit var sensorManager: AudioSensorManager
    private lateinit var context: Context
    private lateinit var audioManager: AudioManager
    private lateinit var sensorDao: SensorDao
    private lateinit var serverManager: ServerManager

    private val volumeMusicSensor =
        Sensor(
            id = AudioSensorManager.volMusic.id,
            serverId = 1,
            enabled = true,
            state = "5",
            lastSentState = "5",
            lastSentIcon = AudioSensorManager.volMusic.statelessIcon,
            icon = AudioSensorManager.volMusic.statelessIcon,
            stateType = "int",
        )

    @Before
    fun setUp() {
        sensorManager = AudioSensorManager()
        context = mockk()
        audioManager = mockk(relaxed = true)
        sensorDao = mockk()
        serverManager = mockk()
        val entryPoint = mockk<SensorManager.SensorManagerEntryPoint>()

        mockkStatic(EntryPointAccessors::class)

        every { context.applicationContext } returns context
        every { context.getSystemService(AudioManager::class.java) } returns audioManager
        every { context.getSystemService(AUDIO_SERVICE) } returns audioManager
        every {
            EntryPointAccessors.fromApplication(
                context,
                SensorManager.SensorManagerEntryPoint::class.java,
            )
        } returns entryPoint
        every { entryPoint.sensorDao() } returns sensorDao
        every { entryPoint.serverManager() } returns serverManager

        coEvery { serverManager.servers() } returns listOf(mockk(relaxed = true))
        coEvery {
            sensorDao.getAnyIsEnabled(
                sensorId = any(),
                servers = any(),
                permission = any(),
                enabledByDefault = any(),
            )
        } answers { firstArg<String>() == AudioSensorManager.volMusic.id }
        coEvery { sensorDao.get(any()) } returns emptyList()
        coEvery { sensorDao.get(AudioSensorManager.volMusic.id) } returns listOf(volumeMusicSensor)

        every { audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) } returns 5
        every { audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC) } returns 2
        every { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) } returns 15

        coJustRun { sensorDao.update(any()) }
        coJustRun { sensorDao.replaceAllAttributes(any(), any()) }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `Given missing min and max attributes when requesting update then force update with min and max attributes`() = runTest {
        val updatedSensor = slot<Sensor>()
        val updatedAttributes = slot<List<Attribute>>()
        coEvery { sensorDao.getFull(AudioSensorManager.volMusic.id) } returns mapOf(volumeMusicSensor to emptyList())
        coJustRun { sensorDao.update(capture(updatedSensor)) }
        coJustRun { sensorDao.replaceAllAttributes(AudioSensorManager.volMusic.id, capture(updatedAttributes)) }

        sensorManager.requestSensorUpdate(context)

        updatedSensor.captured.assertForceUpdate()
        assertEquals(
            mapOf("min" to "2", "max" to "15"),
            updatedAttributes.captured.associate { it.name to it.value },
        )
    }

    @Test
    fun `Given changed max attribute when requesting update then force update`() = runTest {
        val updatedSensor = slot<Sensor>()
        coEvery { sensorDao.getFull(AudioSensorManager.volMusic.id) } returns mapOf(
            volumeMusicSensor to listOf(
                Attribute(AudioSensorManager.volMusic.id, "min", "2", "int"),
                Attribute(AudioSensorManager.volMusic.id, "max", "10", "int"),
            ),
        )
        coJustRun { sensorDao.update(capture(updatedSensor)) }

        sensorManager.requestSensorUpdate(context)

        updatedSensor.captured.assertForceUpdate()
    }

    @Test
    fun `Given mismatched max attribute when requesting update twice then force update each time`() = runTest {
        val updatedSensors = mutableListOf<Sensor>()
        coEvery { sensorDao.getFull(AudioSensorManager.volMusic.id) } returns mapOf(
            volumeMusicSensor to listOf(
                Attribute(AudioSensorManager.volMusic.id, "min", "2", "int"),
                Attribute(AudioSensorManager.volMusic.id, "max", "10", "int"),
            ),
        )
        coJustRun { sensorDao.update(capture(updatedSensors)) }

        sensorManager.requestSensorUpdate(context)
        sensorManager.requestSensorUpdate(context)

        assertEquals(2, updatedSensors.size)
        updatedSensors.forEach { it.assertForceUpdate() }
    }

    @Test
    fun `Given multi-server with one server having mismatched attributes then force update`() = runTest {
        val updatedSensors = mutableListOf<Sensor>()
        val volumeMusicSensorServer2 = volumeMusicSensor.copy(serverId = 2)
        coEvery { sensorDao.get(AudioSensorManager.volMusic.id) } returns listOf(volumeMusicSensor, volumeMusicSensorServer2)
        coEvery { sensorDao.getFull(AudioSensorManager.volMusic.id) } returns mapOf(
            volumeMusicSensor to listOf(
                Attribute(AudioSensorManager.volMusic.id, "min", "2", "int"),
                Attribute(AudioSensorManager.volMusic.id, "max", "15", "int"),
            ),
            volumeMusicSensorServer2 to listOf(
                Attribute(AudioSensorManager.volMusic.id, "min", "2", "int"),
                Attribute(AudioSensorManager.volMusic.id, "max", "10", "int"),
            ),
        )
        coJustRun { sensorDao.update(capture(updatedSensors)) }

        sensorManager.requestSensorUpdate(context)

        assertEquals(2, updatedSensors.size)
        updatedSensors.forEach { it.assertForceUpdate() }
    }

    @Test
    fun `Given multi-server with all servers having matching attributes then does not force update`() = runTest {
        val updatedSensor = slot<Sensor>()
        val volumeMusicSensorServer2 = volumeMusicSensor.copy(serverId = 2)
        coEvery { sensorDao.get(AudioSensorManager.volMusic.id) } returns listOf(volumeMusicSensor, volumeMusicSensorServer2)
        coEvery { sensorDao.getFull(AudioSensorManager.volMusic.id) } returns mapOf(
            volumeMusicSensor to listOf(
                Attribute(AudioSensorManager.volMusic.id, "min", "2", "int"),
                Attribute(AudioSensorManager.volMusic.id, "max", "15", "int"),
            ),
            volumeMusicSensorServer2 to listOf(
                Attribute(AudioSensorManager.volMusic.id, "min", "2", "int"),
                Attribute(AudioSensorManager.volMusic.id, "max", "15", "int"),
            ),
        )
        coJustRun { sensorDao.update(capture(updatedSensor)) }

        sensorManager.requestSensorUpdate(context)

        updatedSensor.captured.assertNoForceUpdate()
    }

    @Test
    fun `Given unchanged min and max attributes when requesting update then does not force update`() = runTest {
        val updatedSensor = slot<Sensor>()
        coEvery { sensorDao.getFull(AudioSensorManager.volMusic.id) } returns mapOf(
            volumeMusicSensor to listOf(
                Attribute(AudioSensorManager.volMusic.id, "min", "2", "int"),
                Attribute(AudioSensorManager.volMusic.id, "max", "15", "int"),
            ),
        )
        coJustRun { sensorDao.update(capture(updatedSensor)) }

        sensorManager.requestSensorUpdate(context)

        updatedSensor.captured.assertNoForceUpdate()
    }

    @Test
    fun `Given unchanged attributes when requesting update twice then getFull is only called once`() = runTest {
        coEvery { sensorDao.getFull(AudioSensorManager.volMusic.id) } returns mapOf(
            volumeMusicSensor to listOf(
                Attribute(AudioSensorManager.volMusic.id, "min", "2", "int"),
                Attribute(AudioSensorManager.volMusic.id, "max", "15", "int"),
            ),
        )

        sensorManager.requestSensorUpdate(context)
        sensorManager.requestSensorUpdate(context)

        coVerify(exactly = 1) { sensorDao.getFull(AudioSensorManager.volMusic.id) }
    }

    @Test
    @Config(sdk = [26]) // Build.VERSION_CODES.O
    fun `Given SDK below P when requesting update then min defaults to 0`() = runTest {
        val updatedAttributes = slot<List<Attribute>>()
        coEvery { sensorDao.getFull(AudioSensorManager.volMusic.id) } returns mapOf(volumeMusicSensor to emptyList())
        coJustRun { sensorDao.update(any()) }
        coJustRun { sensorDao.replaceAllAttributes(AudioSensorManager.volMusic.id, capture(updatedAttributes)) }

        sensorManager.requestSensorUpdate(context)

        assertEquals(
            mapOf("min" to "0", "max" to "15"),
            updatedAttributes.captured.associate { it.name to it.value },
        )
    }

    @Test
    fun `Given SDK at or above P when requesting update then min comes from AudioManager`() = runTest {
        val updatedAttributes = slot<List<Attribute>>()
        coEvery { sensorDao.getFull(AudioSensorManager.volMusic.id) } returns mapOf(volumeMusicSensor to emptyList())
        coJustRun { sensorDao.update(any()) }
        coJustRun { sensorDao.replaceAllAttributes(AudioSensorManager.volMusic.id, capture(updatedAttributes)) }

        sensorManager.requestSensorUpdate(context)

        assertEquals(
            mapOf("min" to "2", "max" to "15"),
            updatedAttributes.captured.associate { it.name to it.value },
        )
    }

    @Test
    fun `Given mismatched attributes when requesting update three times then cache kicks in after force update succeeds`() = runTest {
        val updatedSensors = mutableListOf<Sensor>()

        // First two calls: DB still returns old (mismatched) attributes → force each time.
        coEvery { sensorDao.getFull(AudioSensorManager.volMusic.id) } returns mapOf(
            volumeMusicSensor to listOf(
                Attribute(AudioSensorManager.volMusic.id, "min", "2", "int"),
                Attribute(AudioSensorManager.volMusic.id, "max", "10", "int"),
            ),
        )
        coJustRun { sensorDao.update(capture(updatedSensors)) }

        sensorManager.requestSensorUpdate(context)
        sensorManager.requestSensorUpdate(context)

        assertEquals(2, updatedSensors.size)
        updatedSensors.forEach { it.assertForceUpdate() }

        // Simulate that the force update has now persisted the correct attributes.
        updatedSensors.clear()
        coEvery { sensorDao.getFull(AudioSensorManager.volMusic.id) } returns mapOf(
            volumeMusicSensor to listOf(
                Attribute(AudioSensorManager.volMusic.id, "min", "2", "int"),
                Attribute(AudioSensorManager.volMusic.id, "max", "15", "int"),
            ),
        )

        sensorManager.requestSensorUpdate(context)

        // Third call: attributes now match → cached, no more force update.
        assertEquals(1, updatedSensors.size)
        updatedSensors.single().assertNoForceUpdate()

        // Verify that getFull was called 3 times total (twice mismatched + once to confirm match).
        coVerify(exactly = 3) { sensorDao.getFull(AudioSensorManager.volMusic.id) }

        // Fourth call: should use cache, no additional getFull call.
        updatedSensors.clear()
        sensorManager.requestSensorUpdate(context)

        assertEquals(1, updatedSensors.size)
        updatedSensors.single().assertNoForceUpdate()
        coVerify(exactly = 3) { sensorDao.getFull(AudioSensorManager.volMusic.id) }
    }

    private fun Sensor.assertForceUpdate() {
        assertNull("Expected lastSentState to be null (force update), but was '$lastSentState'", lastSentState)
        assertNull("Expected lastSentIcon to be null (force update), but was '$lastSentIcon'", lastSentIcon)
    }

    private fun Sensor.assertNoForceUpdate() {
        assertEquals("Expected lastSentState to be preserved (no force update)", "5", lastSentState)
        assertEquals("Expected lastSentIcon to be preserved (no force update)", AudioSensorManager.volMusic.statelessIcon, lastSentIcon)
    }
}
