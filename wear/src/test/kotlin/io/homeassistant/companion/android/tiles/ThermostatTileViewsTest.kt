package io.homeassistant.companion.android.tiles

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.wear.protolayout.LayoutElementBuilders.Arc
import androidx.wear.protolayout.LayoutElementBuilders.ArcAdapter
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.TimelineBuilders.Timeline
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.ClimateControls
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithoutContext
import io.homeassistant.companion.android.database.wear.ThermostatTile
import io.mockk.mockk
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Layout traversal adapted from the test approach in home-assistant/android PR #7877. */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class ThermostatTileViewsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val views = ThermostatTileViews(context)
    private val prefix = ThermostatCommandCoordinator.Target(1, "climate.living_room").actionPrefix
    private val config = ThermostatTile(id = 10, entityId = "climate.living_room", showPowerButton = true)

    private fun thermostat(state: String = "heat", supportsPower: Boolean = true) = EntityDisplayWithoutContext(
        entityId = "climate.living_room",
        name = "Living Room",
        icon = mockk(),
        rawState = state,
        climateControls = ClimateControls(
            currentTemperature = 19.4f,
            targetTemperature = 19.5f,
            targetTemperatureStep = 0.5f,
            hvacAction = if (state == "heat") "heating" else "idle",
            minTemperature = 5f,
            maxTemperature = 30f,
            temperatureUnit = "°C",
            hvacModes = listOf("off", "heat", "cool", "heat_cool"),
            supportsTargetTemperature = true,
            targetTemperatureLow = 18f,
            targetTemperatureHigh = 22f,
            supportsTargetTemperatureRange = true,
            fanMode = "auto",
            fanModes = listOf("auto", "on"),
            supportsTurnOn = supportsPower,
            supportsTurnOff = supportsPower,
        ),
    )

    private fun LayoutElement.descendants(): List<LayoutElement> = listOf(this) + when (this) {
        is Box -> contents
        is Column -> contents
        is Row -> contents
        is Arc -> contents.filterIsInstance<ArcAdapter>().mapNotNull { it.content }
        else -> emptyList()
    }.flatMap { it.descendants() }

    private fun Timeline.elements(): List<LayoutElement> = timelineEntries.first().layout!!.root!!.descendants()

    private fun Timeline.actions(): Set<String> = elements().filterIsInstance<Box>()
        .mapNotNull { it.modifiers?.clickable?.id }
        .filter { it.startsWith(prefix) }
        .map { it.removePrefix(prefix) }
        .toSet()

    private fun classic(state: String = "heat", showPower: Boolean = true, supportsPower: Boolean = true) =
        views.classicTimeline(config.copy(showPowerButton = showPower), thermostat(state, supportsPower), "°C", null, prefix)

    @Test
    fun `classic retains the two original controls when power is hidden or unsupported`() {
        assertEquals(setOf("Down", "Up"), classic(showPower = false).actions())
        assertEquals(setOf("Down", "Up"), classic(supportsPower = false).actions())
    }

    @Test
    fun `classic power control respects thermostat state`() {
        assertEquals(setOf("Down", "Up", "PowerOff"), classic().actions())
        assertEquals(setOf("PowerOn"), classic(state = "off").actions())
        assertTrue(classic(state = "unavailable").actions().isEmpty())
    }

    @Test
    fun `enhanced single and dual setpoint actions stay distinct on compact watches`() {
        val enhanced = config.copy(layoutStyle = "enhanced")
        val single = views.enhancedTimeline(enhanced, thermostat(), "°C", null, prefix, 192, 192).actions()
        val dual = views.enhancedTimeline(enhanced, thermostat("heat_cool"), "°C", null, prefix, 192, 192).actions()
        val modes = setOf("Mode:heat", "Mode:cool", "Mode:heat_cool", "Mode:off", "CycleFan")
        assertEquals(modes + setOf("Temp:single:-3", "Temp:single:-1", "Temp:single:1", "Temp:single:3"), single)
        assertEquals(modes + listOf("heat", "cool").flatMap { slot -> listOf(-3, -1, 1, 3).map { "Temp:$slot:$it" } }, dual)
    }

    @Test
    fun `enhanced displays pending feedback and proposed setpoint without displaying failed values`() {
        val enhanced = config.copy(layoutStyle = "enhanced")
        val waiting = ThermostatCommandCoordinator.Pending("21°", ThermostatCommandCoordinator.Pending.Status.WAITING, single = 21f)
        fun texts(pending: ThermostatCommandCoordinator.Pending) = views.enhancedTimeline(
            enhanced, thermostat(), "°C", pending, prefix, 192, 192,
        ).elements().filterIsInstance<Text>().mapNotNull { it.text?.value }
        val waitingTexts = texts(waiting)
        assertTrue(context.getString(commonR.string.thermostat_tile_waiting) in waitingTexts)
        assertTrue("21°" in waitingTexts)
        val failedTexts = texts(waiting.copy(status = ThermostatCommandCoordinator.Pending.Status.FAILED))
        assertTrue(context.getString(commonR.string.thermostat_tile_unconfirmed) in failedTexts)
        assertFalse("21°" in failedTexts)
        assertTrue("19.5°" in failedTexts)
    }
}
