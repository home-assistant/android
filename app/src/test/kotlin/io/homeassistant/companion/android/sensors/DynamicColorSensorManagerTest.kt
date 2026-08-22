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
@Config(sdk = [36], application = HiltTestApplication::class)
class DynamicColorSensorManagerTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    internal lateinit var sensorManager: DynamicColorSensorManager

    @Inject
    internal lateinit var sensorRepository: SensorRepository

    @Before
    fun setUp() {
        getApplicationContext<Context>().seedFakeAndroidId()
        hiltRule.inject()
    }

    @Config(maxSdk = Build.VERSION_CODES.R)
    @Test
    fun `Given SDK is lower than Android 12 then sensor manager is absent`() {
        assertFalse(sensorManager.hasSensor())
    }

    @Config(minSdk = Build.VERSION_CODES.S)
    @Test
    fun `Given SDK is at least Android 12 then sensor manager is present`() {
        assertTrue(sensorManager.hasSensor())
    }

    @Test
    fun `Given dynamic color sensor when available sensors then includes color and palette sensors`() = runTest {
        val availableSensors = sensorManager.getAvailableSensors()
        assertTrue(availableSensors.contains(DynamicColorSensorManager.accentColorSensor))
        assertTrue(availableSensors.contains(DynamicColorSensorManager.tonalPaletteSensor))
    }

    @Test
    fun `Given accent color sensor when required permissions then none specified`() {
        assertArrayEquals(
            emptyArray<String>(),
            sensorManager.requiredPermissions(DynamicColorSensorManager.accentColorSensor.id),
        )
    }

    @Test
    fun `Given tonal palette sensor when required permissions then none specified`() {
        assertArrayEquals(
            emptyArray<String>(),
            sensorManager.requiredPermissions(DynamicColorSensorManager.tonalPaletteSensor.id),
        )
    }

    @Test
    fun `Given enabled color sensor when request update then sets theme color`() = runTest {
        val id = DynamicColorSensorManager.accentColorSensor.id
        sensorRepository.setSensorEnabled(id, listOf(1), true)

        sensorManager.requestSensorUpdate()

        val state = sensorRepository.get(id).single().state

        // Default accent color for Robolectric
        assertEquals("#475D92", state)

        val attrs = getSensorAttributes(id)
        val rgbColor = attrs.find { it.name == "rgb_color" }
        assertNotNull(rgbColor)

        // Fixed accent color for Robolectric is 475D92
        assertEquals("[71,93,146]", rgbColor.value)
    }

    @Test
    fun `Given disabled color sensor when request update then does not update state`() = runTest {
        val id = DynamicColorSensorManager.accentColorSensor.id
        sensorRepository.setSensorEnabled(id, listOf(1), false)

        sensorManager.requestSensorUpdate()

        assertEquals("", sensorRepository.get(id).single().state)
    }

    @Test
    fun `Given palette sensor when request update then sets palette variant`() = runTest {
        val id = DynamicColorSensorManager.tonalPaletteSensor.id
        sensorRepository.setSensorEnabled(id, listOf(1), true)

        Settings.Secure.putString(
            getApplicationContext<Context>().contentResolver,
            "theme_customization_overlay_packages",
            """{ "android.theme.customization.theme_style": "VIBRANT" }""",
        )

        sensorManager.requestSensorUpdate()

        assertEquals("VIBRANT", sensorRepository.get(id).single().state)
    }

    @Test
    fun `Given palette sensor when receives malformed input then state is unknown`() = runTest {
        val id = DynamicColorSensorManager.tonalPaletteSensor.id
        sensorRepository.setSensorEnabled(id, listOf(1), true)

        Settings.Secure.putString(
            getApplicationContext<Context>().contentResolver,
            "theme_customization_overlay_packages",
            """android.theme.customization.theme_style""",
        )

        sensorManager.requestSensorUpdate()

        assertEquals(STATE_UNKNOWN, sensorRepository.get(id).single().state)
    }

    @Test
    fun `Given palette sensor when receives null input then state is unavailable`() = runTest {
        val id = DynamicColorSensorManager.tonalPaletteSensor.id
        sensorRepository.setSensorEnabled(id, listOf(1), true)

        Settings.Secure.putString(
            getApplicationContext<Context>().contentResolver,
            "theme_customization_overlay_packages",
            null,
        )

        sensorManager.requestSensorUpdate()

        assertEquals(STATE_UNAVAILABLE, sensorRepository.get(id).single().state)
    }

    @Test
    fun `Given palette sensor when updated then options are exhaustive`() = runTest {
        val id = DynamicColorSensorManager.tonalPaletteSensor.id
        sensorRepository.setSensorEnabled(id, listOf(1), true)

        sensorManager.requestSensorUpdate()

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
