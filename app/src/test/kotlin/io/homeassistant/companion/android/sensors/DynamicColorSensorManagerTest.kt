package io.homeassistant.companion.android.sensors

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.sensors.SensorRepository
import io.homeassistant.companion.android.common.util.STATE_UNAVAILABLE
import io.homeassistant.companion.android.common.util.STATE_UNKNOWN
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import io.homeassistant.companion.android.database.sensor.Attribute
import io.homeassistant.companion.android.testing.unit.seedFakeAndroidId
import javax.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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
    internal lateinit var sensorRepository: SensorRepository

    @Inject
    internal lateinit var serverManager: ServerManager

    @Before
    fun setUp() {
        getApplicationContext<Context>().seedFakeAndroidId()
        hiltRule.inject()
    }

    private fun createManager(isAutomotive: Boolean = false) = DynamicColorSensorManager(
        applicationContext = getApplicationContext(),
        isAutomotive = isAutomotive,
        sensorRepository = sensorRepository,
        serverManager = serverManager,
    )

    @Config(maxSdk = Build.VERSION_CODES.R)
    @Test
    fun `Given SDK is lower than Android 12 then sensor manager is absent`() {
        assertFalse(createManager().hasSensor())
    }

    @Config(minSdk = Build.VERSION_CODES.S)
    @Test
    fun `Given SDK is at least Android 12 then sensor manager is present`() {
        assertTrue(createManager().hasSensor())
    }

    @Test
    fun `Given dynamic color sensor when available sensors then includes color and palette sensors`() = runTest {
        val availableSensors = createManager().getAvailableSensors()
        assertTrue(availableSensors.contains(DynamicColorSensorManager.accentColorSensor))
        assertTrue(availableSensors.contains(DynamicColorSensorManager.tonalPaletteSensor))
    }

    @Test
    fun `Given automotive when available sensors then only accent color is present`() = runTest {
        val availableSensors = createManager(isAutomotive = true).getAvailableSensors()
        assertTrue(availableSensors.contains(DynamicColorSensorManager.accentColorSensor))
        assertFalse(availableSensors.contains(DynamicColorSensorManager.tonalPaletteSensor))
    }

    @Test
    fun `Given automotive when request update then tonal palette is not updated`() = runTest {
        val id = DynamicColorSensorManager.tonalPaletteSensor.id
        sensorRepository.setSensorEnabled(id, listOf(1), true)
        Settings.Secure.putString(
            getApplicationContext<Context>().contentResolver,
            "theme_customization_overlay_packages",
            """{ "android.theme.customization.theme_style": "VIBRANT" }""",
        )

        createManager(isAutomotive = true).requestSensorUpdate()

        // Left at its default because automotive skips the tonal palette update.
        assertEquals("", sensorRepository.get(id).single().state)
    }

    @Test
    fun `Given automotive when request update then accent color is still updated`() = runTest {
        val id = DynamicColorSensorManager.accentColorSensor.id
        sensorRepository.setSensorEnabled(id, listOf(1), true)

        createManager(isAutomotive = true).requestSensorUpdate()

        assertTrue(sensorRepository.get(id).single().state.startsWith("#"))
    }

    @Test
    fun `Given accent color sensor when required permissions then none specified`() {
        assertArrayEquals(
            emptyArray<String>(),
            createManager().requiredPermissions(DynamicColorSensorManager.accentColorSensor.id),
        )
    }

    @Test
    fun `Given tonal palette sensor when required permissions then none specified`() {
        assertArrayEquals(
            emptyArray<String>(),
            createManager().requiredPermissions(DynamicColorSensorManager.tonalPaletteSensor.id),
        )
    }

    @Test
    fun `Given enabled color sensor when request update then sets theme color`() = runTest {
        val id = DynamicColorSensorManager.accentColorSensor.id
        sensorRepository.setSensorEnabled(id, listOf(1), true)

        createManager().requestSensorUpdate()

        val state = sensorRepository.get(id).single().state

        val context = DynamicColors.wrapContextIfAvailable(getApplicationContext())
        val color = MaterialColors.getColor(context, android.R.attr.colorAccent, 0)
        val colorStr = "%06X".format(color and 0x00FFFFFF)

        assertEquals("#$colorStr", state)

        val attrs = getSensorAttributes(id)
        val rgbColor = attrs.find { it.name == "rgb_color" }
        assertNotNull(rgbColor)

        assertEquals("[${Color.red(color)},${Color.green(color)},${Color.blue(color)}]", rgbColor.value)
    }

    @Test
    fun `Given disabled color sensor when request update then does not update state`() = runTest {
        val id = DynamicColorSensorManager.accentColorSensor.id
        sensorRepository.setSensorEnabled(id, listOf(1), false)

        createManager().requestSensorUpdate()

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

        createManager().requestSensorUpdate()

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

        createManager().requestSensorUpdate()

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

        createManager().requestSensorUpdate()

        assertEquals(STATE_UNAVAILABLE, sensorRepository.get(id).single().state)
    }

    @Test
    fun `Given palette sensor when updated then options are exhaustive`() = runTest {
        val id = DynamicColorSensorManager.tonalPaletteSensor.id
        sensorRepository.setSensorEnabled(id, listOf(1), true)

        createManager().requestSensorUpdate()

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
