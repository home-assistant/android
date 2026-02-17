package io.homeassistant.companion.android.common.sensors

import android.content.Context
import android.content.Context.AUDIO_SERVICE
import android.media.AudioManager
import dagger.hilt.android.EntryPointAccessors
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.sensor.Attribute
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.database.sensor.SensorDao
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ConsoleLogExtension::class)
class AudioSensorManagerTest {

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

    @BeforeEach
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
        every { audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC) } returns 0
        every { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) } returns 15

        coJustRun { sensorDao.update(any()) }
        coJustRun { sensorDao.replaceAllAttributes(any(), any()) }
    }

    @AfterEach
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

        assertNull(updatedSensor.captured.lastSentState)
        assertNull(updatedSensor.captured.lastSentIcon)
        assertEquals(
            mapOf("min" to "0", "max" to "15"),
            updatedAttributes.captured.associate { it.name to it.value },
        )
    }

    @Test
    fun `Given changed max attribute when requesting update then force update`() = runTest {
        val updatedSensor = slot<Sensor>()
        coEvery { sensorDao.getFull(AudioSensorManager.volMusic.id) } returns mapOf(
            volumeMusicSensor to listOf(
                Attribute(AudioSensorManager.volMusic.id, "min", "0", "int"),
                Attribute(AudioSensorManager.volMusic.id, "max", "10", "int"),
            ),
        )
        coJustRun { sensorDao.update(capture(updatedSensor)) }

        sensorManager.requestSensorUpdate(context)

        assertNull(updatedSensor.captured.lastSentState)
        assertNull(updatedSensor.captured.lastSentIcon)
    }

    @Test
    fun `Given unchanged min and max attributes when requesting update then does not force update`() = runTest {
        val updatedSensor = slot<Sensor>()
        coEvery { sensorDao.getFull(AudioSensorManager.volMusic.id) } returns mapOf(
            volumeMusicSensor to listOf(
                Attribute(AudioSensorManager.volMusic.id, "min", "0", "int"),
                Attribute(AudioSensorManager.volMusic.id, "max", "15", "int"),
            ),
        )
        coJustRun { sensorDao.update(capture(updatedSensor)) }

        sensorManager.requestSensorUpdate(context)

        assertEquals("5", updatedSensor.captured.lastSentState)
        assertEquals(AudioSensorManager.volMusic.statelessIcon, updatedSensor.captured.lastSentIcon)
    }
}
