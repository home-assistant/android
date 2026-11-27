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
import io.homeassistant.companion.android.database.wear.ThermostatLayoutStyle
import io.homeassistant.companion.android.database.wear.ThermostatTemperatureUnit
import io.homeassistant.companion.android.database.wear.ThermostatTile
import io.mockk.mockk
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Layout traversal adapted from the test approach in https://github.com/home-assistant/android/pull/7877. */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class ThermostatTileViewsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val views = ThermostatTileViews(context)
    private val prefix = ThermostatCommandCoordinator.Target(1, "climate.living_room").actionPrefix
    private val config = ThermostatTile(id = 10, entityId = "climate.living_room")

    private fun thermostat(
        state: String = "heat",
        hvacAction: String = if (state == "heat") "heating" else "idle",
        fanMode: String = "auto",
        fanModes: List<String> = listOf("auto", "on"),
    ) = EntityDisplayWithoutContext(
        entityId = "climate.living_room",
        name = "Living Room",
        icon = mockk(),
        rawState = state,
        climateControls = ClimateControls(
            currentTemperature = 19.4f,
            targetTemperature = if (state == "heat_cool") null else 19.5f,
            targetTemperatureStep = 0.5f,
            hvacAction = hvacAction,
            minTemperature = 5f,
            maxTemperature = 30f,
            temperatureUnit = "°C",
            hvacModes = listOf("off", "heat", "cool", "heat_cool"),
            supportsTargetTemperature = state != "heat_cool",
            targetTemperatureLow = 18f,
            targetTemperatureHigh = 22f,
            fanMode = fanMode,
            fanModes = fanModes,
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

    private fun classic(state: String = "heat") = views.classicTimeline(config, thermostat(state), "°C", null, prefix)

    @Test
    fun `Given Classic layout when rendered then retain adjustment controls without power actions`() {
        assertEquals(setOf("Down", "Up"), classic().actions())
        assertEquals(setOf("Down", "Up"), classic(state = "cool").actions())
        assertTrue(classic(state = "off").actions().isEmpty())
        assertTrue(classic(state = "unavailable").actions().isEmpty())
        assertTrue(classic(state = "unknown").actions().isEmpty())
    }

    @Test
    fun `Given compact Enhanced layout when rendered then single and dual setpoint actions stay distinct`() {
        val enhanced = config.copy(
            layoutStyle = ThermostatLayoutStyle.ENHANCED,
            showSecondaryButtons = true,
            showFanControl = true,
        )
        val single = views.enhancedTimeline(enhanced, thermostat(), "°C", null, prefix, 192, 192).actions()
        val dual = views.enhancedTimeline(enhanced, thermostat("heat_cool"), "°C", null, prefix, 192, 192).actions()
        val modes = setOf("Mode:heat", "Mode:cool", "Mode:heat_cool", "Mode:off", "CycleFan")
        assertEquals(modes + setOf("Temp:single:-3", "Temp:single:-1", "Temp:single:1", "Temp:single:3"), single)
        assertEquals(modes + listOf("heat", "cool").flatMap { slot -> listOf(-3, -1, 1, 3).map { "Temp:$slot:$it" } }, dual)
    }

    @Test
    fun `Given default Enhanced settings when rendered then hide secondary adjustments and fan on both watch sizes`() {
        val enhanced = config.copy(layoutStyle = ThermostatLayoutStyle.ENHANCED)
        val modes = setOf("Mode:heat", "Mode:cool", "Mode:heat_cool", "Mode:off")
        for (size in listOf(192, 240)) {
            for (state in listOf("heat", "cool")) {
                val single = views.enhancedTimeline(enhanced, thermostat(state), "°C", null, prefix, size, size)
                assertEquals(modes + setOf("Temp:single:-1", "Temp:single:1"), single.actions())
            }
            val dual = views.enhancedTimeline(enhanced, thermostat("heat_cool"), "°C", null, prefix, size, size)
            assertEquals(modes + setOf("Temp:heat:-1", "Temp:heat:1", "Temp:cool:-1", "Temp:cool:1"), dual.actions())
            val off = views.enhancedTimeline(enhanced, thermostat("off"), "°C", null, prefix, size, size)
            assertEquals(modes, off.actions())
        }
    }

    @Test
    fun `Given secondary adjustments enabled when rendered then use the saved step on both watch sizes`() {
        val enhanced = config.copy(
            layoutStyle = ThermostatLayoutStyle.ENHANCED,
            showSecondaryButtons = true,
            secondaryStep = 5,
            showFanControl = true,
        )
        val modes = setOf("Mode:heat", "Mode:cool", "Mode:heat_cool", "Mode:off", "CycleFan")
        for (size in listOf(192, 240)) {
            val single = views.enhancedTimeline(enhanced, thermostat(), "°C", null, prefix, size, size)
            val dual = views.enhancedTimeline(enhanced, thermostat("heat_cool"), "°C", null, prefix, size, size)
            assertEquals(modes + setOf("Temp:single:-5", "Temp:single:-1", "Temp:single:1", "Temp:single:5"), single.actions())
            assertEquals(modes + listOf("heat", "cool").flatMap { slot -> listOf(-5, -1, 1, 5).map { "Temp:$slot:$it" } }, dual.actions())
        }
    }

    @Test
    fun `Given fan preference and capability when rendered then show fan only when both are present`() {
        val enhanced = config.copy(layoutStyle = ThermostatLayoutStyle.ENHANCED, showFanControl = true)
        for (size in listOf(192, 240)) {
            val supported = views.enhancedTimeline(enhanced, thermostat(), "°C", null, prefix, size, size)
            val unsupported = views.enhancedTimeline(
                enhanced,
                thermostat(fanModes = emptyList()),
                "°C",
                null,
                prefix,
                size,
                size,
            )
            assertTrue("CycleFan" in supported.actions())
            assertFalse("CycleFan" in unsupported.actions())
        }
    }

    @Test
    fun `Given secondary preference when rendered then all visible adjustment buttons use the expected background`() {
        for (size in listOf(192, 240)) {
            for (state in listOf("heat", "cool", "heat_cool")) {
                for (secondary in listOf(false, true)) {
                    val enhanced = config.copy(
                        layoutStyle = ThermostatLayoutStyle.ENHANCED,
                        showSecondaryButtons = secondary,
                    )
                    val buttons = views.enhancedTimeline(enhanced, thermostat(state), "°C", null, prefix, size, size)
                        .elements().filterIsInstance<Box>()
                        .filter { it.modifiers?.clickable?.id?.startsWith(prefix + "Temp:") == true }
                    val expectedCount = (if (state == "heat_cool") 2 else 1) * (if (secondary) 4 else 2)
                    assertEquals(expectedCount, buttons.size)
                    buttons.forEach { button ->
                        assertEquals(
                            if (secondary) 0xFF293447.toInt() else 0x00000000,
                            button.modifiers?.background?.color?.argb,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `Given a unit override when rendering both layouts then show converted temperatures and degree symbols`() {
        val fahrenheit = config.copy(temperatureUnit = ThermostatTemperatureUnit.FAHRENHEIT)
        val classicTexts = views.classicTimeline(fahrenheit, thermostat(), "°C", null, prefix)
            .elements().filterIsInstance<Text>().mapNotNull { it.text?.value }
        assertTrue("67.1 °F" in classicTexts)
        assertTrue("66.9 °F" in classicTexts)
        val enhancedTexts = views.enhancedTimeline(fahrenheit, thermostat(), "°C", null, prefix, 240, 240)
            .elements().filterIsInstance<Text>().mapNotNull { it.text?.value }
        assertTrue("66.9° F" in enhancedTexts)
        assertTrue("67.1°" in enhancedTexts)
        val rangeTexts = views.enhancedTimeline(fahrenheit, thermostat("heat_cool"), "°C", null, prefix, 240, 240)
            .elements().filterIsInstance<Text>().mapNotNull { it.text?.value }
        assertTrue("H64.4°" in rangeTexts)
        assertTrue("C71.6°" in rangeTexts)
        val defaultTexts = views.enhancedTimeline(config, thermostat(), "°C", null, prefix, 240, 240)
            .elements().filterIsInstance<Text>().mapNotNull { it.text?.value }
        assertTrue("19.4° C" in defaultTexts)
    }

    @Test
    fun `Given HVAC off when fan activity changes then Enhanced displays the reported activity`() {
        fun texts(action: String, fanMode: String) = views.enhancedTimeline(
            config.copy(layoutStyle = ThermostatLayoutStyle.ENHANCED),
            thermostat("off", hvacAction = action, fanMode = fanMode),
            "°C",
            null,
            prefix,
            240,
            240,
        ).elements().filterIsInstance<Text>().mapNotNull { it.text?.value }
        val off = context.getString(commonR.string.state_off).uppercase(java.util.Locale.getDefault())
        val fanOnly = context.getString(
            commonR.string.thermostat_tile_activity_summary,
            off,
            context.getString(commonR.string.state_fan_only),
        )
        assertTrue(fanOnly in texts("fan", "on"))
        assertTrue(fanOnly in texts("fan", "auto"))
        assertTrue(fanOnly in texts("fan_only", "on"))
        assertTrue(off in texts("idle", "on"))
        assertFalse(fanOnly in texts("idle", "on"))
        assertTrue(off in texts("off", "auto"))
    }

    @Test
    fun `Given pending or failed commands when rendering Enhanced then show feedback without failed setpoints`() {
        val enhanced = config.copy(layoutStyle = ThermostatLayoutStyle.ENHANCED)
        val waiting = ThermostatCommandCoordinator.Pending("21°", ThermostatCommandCoordinator.Pending.Status.WAITING, single = 21f)
        fun texts(pending: ThermostatCommandCoordinator.Pending) = views.enhancedTimeline(
            enhanced,
            thermostat(),
            "°C",
            pending,
            prefix,
            192,
            192,
        ).elements().filterIsInstance<Text>().mapNotNull { it.text?.value }
        val waitingTexts = texts(waiting)
        assertTrue(context.getString(commonR.string.thermostat_tile_waiting) in waitingTexts)
        assertTrue("21°" in waitingTexts)
        val failedTexts = texts(waiting.copy(status = ThermostatCommandCoordinator.Pending.Status.FAILED))
        assertTrue(context.getString(commonR.string.thermostat_tile_unconfirmed) in failedTexts)
        assertFalse("21°" in failedTexts)
        assertTrue("19.5°" in failedTexts)
    }

    @Test
    fun `Given a pending Classic command when rendered then feedback replaces the normal status`() {
        val waiting = ThermostatCommandCoordinator.Pending(
            "21°",
            ThermostatCommandCoordinator.Pending.Status.WAITING,
            single = 21f,
        )
        fun texts(pending: ThermostatCommandCoordinator.Pending) = views.classicTimeline(
            config,
            thermostat(),
            "°C",
            pending,
            prefix,
        ).elements().filterIsInstance<Text>().mapNotNull { it.text?.value }
        val waitingTexts = texts(waiting)
        assertTrue(context.getString(commonR.string.thermostat_tile_waiting) in waitingTexts)
        assertTrue("21.0 °C" in waitingTexts)
        val failedTexts = texts(waiting.copy(status = ThermostatCommandCoordinator.Pending.Status.FAILED))
        assertTrue(context.getString(commonR.string.thermostat_tile_unconfirmed) in failedTexts)
        assertTrue("19.5 °C" in failedTexts)
    }

    @Test
    fun `Given unavailable Enhanced climate when rendered then only refresh remains actionable`() {
        for (size in listOf(192, 240)) {
            for (state in listOf("unavailable", "unknown")) {
                val timeline = views.enhancedTimeline(config, thermostat(state), "°C", null, prefix, size, size)
                assertTrue(timeline.actions().isEmpty())
                assertEquals(MODIFIER_CLICK_REFRESH, (timeline.elements().first() as Box).modifiers?.clickable?.id)
            }
        }
    }

    @Test
    fun `Given manual refresh when either layout is rendered then background is clickable`() {
        val manual = config.copy(refreshInterval = 0L)
        for (size in listOf(192, 240)) {
            val enhanced = views.enhancedTimeline(manual, thermostat(), "°C", null, prefix, size, size)
            assertEquals(MODIFIER_CLICK_REFRESH, (enhanced.elements().first() as Box).modifiers?.clickable?.id)
            assertTrue("Temp:single:1" in enhanced.actions())
        }
        val classic = views.classicTimeline(manual, thermostat(), "°C", null, prefix)
        assertEquals(MODIFIER_CLICK_REFRESH, (classic.elements().first() as Box).modifiers?.clickable?.id)
    }

    @Test
    fun `Given Classic AUTO mode when rendered then display both setpoints and adjustment controls`() {
        val timeline = classic(state = "heat_cool")
        val texts = timeline.elements().filterIsInstance<Text>().mapNotNull { it.text?.value }
        assertTrue("18.0–22.0 °C" in texts)
        assertEquals(setOf("Down", "Up"), timeline.actions())
    }
}
