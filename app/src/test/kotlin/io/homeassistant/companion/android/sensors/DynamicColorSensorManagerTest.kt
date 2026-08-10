package io.homeassistant.companion.android.sensors

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.sensors.SensorRepository
import io.homeassistant.companion.android.common.util.STATE_UNAVAILABLE
import io.homeassistant.companion.android.common.util.STATE_UNKNOWN
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import io.homeassistant.companion.android.database.sensor.Attribute
import io.homeassistant.companion.android.testing.unit.seedFakeAndroidId
import javax.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class DynamicColorSensorManagerTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    internal lateinit var dynamicColorSensor: DynamicColorSensorManager

    @Inject
    internal lateinit var sensorRepository: SensorRepository

    @Before
    fun setUp() {
        getApplicationContext<Context>().seedFakeAndroidId()
        hiltRule.inject()
    }

    @Config(maxSdk = Build.VERSION_CODES.R)
    @Test
    fun `Given SDK is lower than Android 12 sensor is absent`() {
        assertFalse(dynamicColorSensor.hasSensor())
    }

    @Config(minSdk = Build.VERSION_CODES.S)
    @Test
    fun `Given SDK is at least Android 12 sensor is present`() {
        assertTrue(dynamicColorSensor.hasSensor())
    }

    @Test
    fun `Available sensors includes color sensor`() = runTest {
        val availableSensors = dynamicColorSensor.getAvailableSensors()
        assertTrue(availableSensors.contains(DynamicColorSensorManager.accentColorSensor))
    }

    @Test
    fun `Available sensors includes palette sensor`() = runTest {
        val availableSensors = dynamicColorSensor.getAvailableSensors()
        assertTrue(availableSensors.contains(DynamicColorSensorManager.tonalPaletteSensor))
    }

    @Test
    fun `Color sensor does not require any special permissions`() {
        assertArrayEquals(
            emptyArray<String>(),
            dynamicColorSensor.requiredPermissions(DynamicColorSensorManager.accentColorSensor.id),
        )
    }

    @Test
    fun `Palette sensor does not require any special permissions`() {
        assertArrayEquals(
            emptyArray<String>(),
            dynamicColorSensor.requiredPermissions(DynamicColorSensorManager.tonalPaletteSensor.id),
        )
    }

    @Test
    fun `Given color sensor is enabled then request update sets theme color`() = runTest {
        val id = DynamicColorSensorManager.accentColorSensor.id
        sensorRepository.setSensorEnabled(id, listOf(1), true)

        dynamicColorSensor.requestSensorUpdate()

        val state = sensorRepository.get(id).single().state

        // Default accent color for Robolectric
        assertEquals("#475D92", state)
    }

    @Test
    fun `Given color sensor is enabled then request update sets rgb color attribute`() = runTest {
        val id = DynamicColorSensorManager.accentColorSensor.id
        sensorRepository.setSensorEnabled(id, listOf(1), true)

        dynamicColorSensor.requestSensorUpdate()

        val attrs = getSensorAttributes(id)
        val rgbColor = attrs.find { it.name == "rgb_color" }
        assertNotNull(rgbColor)

        // Fixed accent color for Robolectric is 475D92
        assertEquals("[71,93,146]", rgbColor.value)
    }

    @Test
    fun `Given color sensor is disabled then request update does not update state`() = runTest {
        val id = DynamicColorSensorManager.accentColorSensor.id
        sensorRepository.setSensorEnabled(id, listOf(1), false)

        dynamicColorSensor.requestSensorUpdate()

        assertEquals("", sensorRepository.get(id).single().state)
    }

    @Test
    fun `Given palette sensor is enabled then request update sets palette variant`() = runTest {
        val id = DynamicColorSensorManager.tonalPaletteSensor.id
        sensorRepository.setSensorEnabled(id, listOf(1), true)

        Settings.Secure.putString(
            getApplicationContext<Context>().contentResolver,
            "theme_customization_overlay_packages",
            """{ "android.theme.customization.theme_style": "VIBRANT" }""",
        )

        dynamicColorSensor.requestSensorUpdate()

        assertEquals("VIBRANT", sensorRepository.get(id).single().state)
    }

    @Test
    fun `Given palette sensor receives malformed input then state unknown`() = runTest {
        val id = DynamicColorSensorManager.tonalPaletteSensor.id
        sensorRepository.setSensorEnabled(id, listOf(1), true)

        Settings.Secure.putString(
            getApplicationContext<Context>().contentResolver,
            "theme_customization_overlay_packages",
            """android.theme.customization.theme_style""",
        )

        dynamicColorSensor.requestSensorUpdate()

        assertEquals(STATE_UNKNOWN, sensorRepository.get(id).single().state)
    }

    @Test
    fun `Given palette sensor receives null input then state is unavailable`() = runTest {
        val id = DynamicColorSensorManager.tonalPaletteSensor.id
        sensorRepository.setSensorEnabled(id, listOf(1), true)

        Settings.Secure.putString(
            getApplicationContext<Context>().contentResolver,
            "theme_customization_overlay_packages",
            null,
        )

        dynamicColorSensor.requestSensorUpdate()

        assertEquals(STATE_UNAVAILABLE, sensorRepository.get(id).single().state)
    }

    @Test
    fun `Given palette sensor is updated then options are exhaustive`() = runTest {
        val id = DynamicColorSensorManager.tonalPaletteSensor.id
        sensorRepository.setSensorEnabled(id, listOf(1), true)

        dynamicColorSensor.requestSensorUpdate()

        val attrs = getSensorAttributes(id)
        val options = attrs.find { it.name == "options" }?.value
        assertNotNull(options)

        // Serialized as ["EXPRESSIVE","RAINBOW",...]
        val optionsUnwrapped = kotlinJsonMapper.decodeFromString<List<String>>(options)

        assertEquals(
            listOf(
                "EXPRESSIVE",
                "FRUIT_SALAD",
                "MONOCHROMATIC",
                "RAINBOW",
                "SPRITZ",
                "TONAL_SPOT",
                "VIBRANT",
            ).sorted(),
            optionsUnwrapped.sorted(),
        )
    }

    private suspend fun getSensorAttributes(id: String): List<Attribute> {
        val map = sensorRepository.getFull(id)
        assertEquals(1, map.size)
        return map.values.single()
    }
}
