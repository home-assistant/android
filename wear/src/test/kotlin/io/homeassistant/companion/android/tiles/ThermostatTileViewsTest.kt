package io.homeassistant.companion.android.tiles

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.wear.protolayout.DimensionBuilders.DpProp
import androidx.wear.protolayout.LayoutElementBuilders.Arc
import androidx.wear.protolayout.LayoutElementBuilders.ArcAdapter
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
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
    private val enhancedConfig = config.copy(layoutStyle = ThermostatLayoutStyle.ENHANCED)

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

    private fun Timeline.texts() = elements().filterIsInstance<Text>().mapNotNull { it.text?.value }

    private fun Timeline.actions(): Set<String> = elements().filterIsInstance<Box>()
        .mapNotNull { it.modifiers?.clickable?.id }
        .filter { it.startsWith(prefix) }
        .map { it.removePrefix(prefix) }
        .toSet()

    private fun Timeline.assertTemperatureBackground(count: Int) {
        val buttons = elements().filterIsInstance<Box>()
            .filter { it.modifiers?.clickable?.id?.startsWith(prefix + "Temp:") == true }
        assertEquals(count, buttons.size)
        for (button in buttons) {
            assertEquals(0xFF293447.toInt(), button.modifiers?.background?.color?.argb)
        }
    }

    private fun classic(state: String = "heat") = views.classicTimeline(config, thermostat(state), "°C", null, prefix)

    @Test
    fun `Given Classic layout when rendered then retain adjustment controls without power actions`() {
        assertEquals(setOf("Down", "Up"), classic().actions())
        assertEquals(setOf("Down", "Up"), classic(state = "cool").actions())
        assertTrue(classic(state = "off").actions().isEmpty())
        assertTrue(classic(state = "unavailable").actions().isEmpty())
        assertTrue(classic(state = "unknown").actions().isEmpty())
    }

    private fun compactTimeline(settings: ThermostatTile, entity: EntityDisplayWithoutContext, menu: String = "") = views.enhancedTimeline(settings, entity, "°C", null, prefix, 192, 192, if (menu.isEmpty()) "" else prefix + menu)

    private fun Timeline.assertAccessibleTargets(size: Float = 48f) {
        val buttons = elements().filterIsInstance<Box>()
            .filter { it.modifiers?.clickable?.id?.startsWith(prefix) == true }
        for (button in buttons) {
            assertEquals(size, (button.width as? DpProp)?.value)
            assertEquals(size, (button.height as? DpProp)?.value)
        }
    }

    @Test
    fun `Given default Enhanced settings when rendered then hide secondary adjustments and fan on both watch sizes`() {
        val modes = setOf("Mode:heat", "Mode:cool", "Mode:heat_cool", "Mode:off")
        for (state in listOf("heat", "cool", "heat_cool", "off")) {
            val slots = when (state) {
                "heat_cool" -> listOf("heat", "cool")
                "off" -> emptyList()
                else -> listOf("single")
            }
            val adjustments = slots.flatMap { listOf("Temp:$it:-1", "Temp:$it:1") }
            val compact = compactTimeline(enhancedConfig, thermostat(state))
            assertEquals(setOf("ModeMenu") + adjustments, compact.actions())
            compact.assertTemperatureBackground(count = adjustments.size)
            compact.assertAccessibleTargets()
            val menu = compactTimeline(enhancedConfig, thermostat(state), "ModeMenu")
            assertEquals(modes + "MenuBack", menu.actions())
            menu.assertAccessibleTargets()
            assertEquals(compact.actions(), compactTimeline(enhancedConfig, thermostat(state), "MenuBack").actions())
            val large = views.enhancedTimeline(enhancedConfig, thermostat(state), "°C", null, prefix, 234, 234)
            assertEquals(modes + adjustments, large.actions())
            large.assertTemperatureBackground(count = adjustments.size)
            large.assertAccessibleTargets(40f)
        }
    }

    @Test
    fun `Given secondary adjustments enabled when rendered then use the saved step on both watch sizes`() {
        val modes = setOf("Mode:heat", "Mode:cool", "Mode:heat_cool", "Mode:off")
        for (step in listOf(3, 5)) {
            for (fan in listOf(false, true)) {
                val enhanced = enhancedConfig.copy(showSecondaryButtons = true, secondaryStep = step, showFanControl = fan)
                val actions = if (fan) modes + "CycleFan" else modes
                for (state in listOf("heat", "cool", "heat_cool")) {
                    val slots = if (state == "heat_cool") listOf("heat", "cool") else listOf("single")
                    val entity = thermostat(state)
                    val compact = compactTimeline(enhanced, entity)
                    assertEquals(
                        setOf("ModeMenu") + slots.flatMap { listOf("Temp:$it:-1", "Temp:$it:1", "AdjustMenu:$it") },
                        compact.actions(),
                    )
                    compact.assertAccessibleTargets()
                    compact.assertTemperatureBackground(count = slots.size * 2)
                    val menu = compactTimeline(enhanced, entity, "ModeMenu")
                    assertEquals(actions + "MenuBack", menu.actions())
                    menu.assertAccessibleTargets()
                    for (slot in slots) {
                        val adjustment = compactTimeline(enhanced, entity, "AdjustMenu:$slot")
                        assertEquals(setOf("Temp:$slot:-$step", "Temp:$slot:$step", "MenuBack"), adjustment.actions())
                        adjustment.assertAccessibleTargets()
                        adjustment.assertTemperatureBackground(count = 2)
                    }
                    val large = views.enhancedTimeline(enhanced, entity, "°C", null, prefix, 234, 234)
                    val amounts = listOf(-step, -1, 1, step)
                    assertEquals(actions + slots.flatMap { slot -> amounts.map { "Temp:$slot:$it" } }, large.actions())
                    large.assertTemperatureBackground(count = slots.size * amounts.size)
                    large.assertAccessibleTargets(40f)
                }
            }
        }
    }

    @Test
    fun `Given fan preference and capability when rendered then show fan only when both are present`() {
        for (enabled in listOf(false, true)) {
            val enhanced = enhancedConfig.copy(showFanControl = enabled)
            for (supported in listOf(false, true)) {
                val entity = thermostat(fanModes = if (supported) listOf("auto", "on") else emptyList())
                val compact = compactTimeline(enhanced, entity, "ModeMenu")
                val large = views.enhancedTimeline(enhanced, entity, "°C", null, prefix, 240, 240)
                assertEquals(enabled && supported, "CycleFan" in compact.actions())
                assertEquals(enabled && supported, "CycleFan" in large.actions())
                compact.assertAccessibleTargets()
            }
        }
    }

    @Test
    fun `Given a unit override when rendering both layouts then show converted temperatures and degree symbols`() {
        val fahrenheit = config.copy(temperatureUnit = ThermostatTemperatureUnit.FAHRENHEIT)
        val classicTexts = views.classicTimeline(fahrenheit, thermostat(), "°C", null, prefix).texts()
        assertTrue("67.1 °F" in classicTexts)
        assertTrue("66.9 °F" in classicTexts)
        val enhancedTexts = views.enhancedTimeline(fahrenheit, thermostat(), "°C", null, prefix, 240, 240).texts()
        assertTrue("66.9° F" in enhancedTexts)
        assertTrue("67.1°" in enhancedTexts)
        val rangeTexts = views.enhancedTimeline(fahrenheit, thermostat("heat_cool"), "°C", null, prefix, 240, 240).texts()
        assertTrue("H64.4°" in rangeTexts)
        assertTrue("C71.6°" in rangeTexts)
        val defaultTexts = views.enhancedTimeline(config, thermostat(), "°C", null, prefix, 240, 240).texts()
        assertTrue("19.4° C" in defaultTexts)
    }

    @Test
    fun `Given HVAC off when fan activity changes then Enhanced displays the reported activity`() {
        fun texts(action: String, fanMode: String) = views.enhancedTimeline(
            enhancedConfig,
            thermostat("off", hvacAction = action, fanMode = fanMode),
            "°C",
            null,
            prefix,
            240,
            240,
        ).texts()
        val off = context.getString(commonR.string.state_off).uppercase(java.util.Locale.getDefault())
        val fanOnly = context.getString(commonR.string.state_fan_only)
        assertTrue(fanOnly in texts("fan", "on"))
        assertTrue(fanOnly in texts("fan", "auto"))
        assertTrue(fanOnly in texts("fan_only", "on"))
        assertTrue(off in texts("idle", "on"))
        assertFalse(fanOnly in texts("idle", "on"))
        assertTrue(off in texts("off", "auto"))
    }

    @Test
    fun `Given pending or failed commands when rendering Enhanced then preserve controls and show feedback`() {
        val waiting = ThermostatCommandCoordinator.Pending("21°", ThermostatCommandCoordinator.Pending.Status.WAITING, single = 21f)
        for (size in listOf(192, 240)) {
            fun timeline(pending: ThermostatCommandCoordinator.Pending?) = views.enhancedTimeline(enhancedConfig, thermostat(), "°C", pending, prefix, size, size)
            val normal = timeline(null)
            val pending = timeline(waiting)
            val failed = timeline(waiting.copy(status = ThermostatCommandCoordinator.Pending.Status.FAILED))
            assertEquals(normal.actions(), pending.actions())
            assertEquals(normal.actions(), failed.actions())
            assertTrue(context.getString(commonR.string.thermostat_tile_waiting) in pending.texts())
            assertTrue(context.getString(commonR.string.thermostat_tile_unconfirmed) in failed.texts())
            assertTrue("21°" in pending.texts())
            assertFalse("21°" in failed.texts())
            assertTrue("19.5°" in failed.texts())
            assertTrue("19.4° C" in normal.texts())
            if (size == 240) {
                val mode = context.getString(commonR.string.state_heat).uppercase(java.util.Locale.getDefault())
                val activity = context.getString(commonR.string.climate_heating)
                assertTrue(mode in normal.texts())
                assertTrue(activity in normal.texts())
                assertFalse(mode in pending.texts())
                assertFalse(activity in failed.texts())
            }
            assertFalse("19.4° C" in pending.texts())
            assertFalse("19.4° C" in failed.texts())
        }
    }

    private fun Timeline.loweredAutoHeader(): Box? = elements().filterIsInstance<Box>().singleOrNull { box ->
        box.contents.size == 2 && box.contents.all { it is Column }
    }

    private fun Column.spacerHeight(index: Int): Float? = ((contents[index] as? Spacer)?.height as? DpProp)?.value

    @Test
    fun `Given direct AUTO with name when feedback changes then retain header offset and controls`() {
        val settings = enhancedConfig.copy(showEntityName = true, showActivity = true)
        val entity = thermostat("heat_cool")
        val waiting = ThermostatCommandCoordinator.Pending(
            "18.5°",
            ThermostatCommandCoordinator.Pending.Status.WAITING,
            low = 18.5f,
            high = 22f,
        )
        fun timeline(pending: ThermostatCommandCoordinator.Pending?) = views.enhancedTimeline(settings, entity, "°C", pending, prefix, 234, 234)
        val normal = timeline(null)
        val cases = listOf(
            null to "19.4° C",
            waiting to context.getString(commonR.string.thermostat_tile_waiting),
            waiting.copy(status = ThermostatCommandCoordinator.Pending.Status.FAILED) to
                context.getString(commonR.string.thermostat_tile_unconfirmed),
        )
        for ((pending, expectedText) in cases) {
            val rendered = timeline(pending)
            val overlay = checkNotNull(rendered.loweredAutoHeader())
            val setpoints = overlay.contents[0] as Column
            val header = overlay.contents[1] as Column
            assertEquals(38f, setpoints.spacerHeight(0))
            assertEquals(1f, setpoints.spacerHeight(1))
            assertEquals(6f, header.spacerHeight(0))
            assertEquals(38f, ((header.contents[1] as Box).height as? DpProp)?.value)
            assertTrue(expectedText in header.descendants().filterIsInstance<Text>().mapNotNull { it.text?.value })
            assertEquals(normal.actions(), rendered.actions())
            rendered.assertAccessibleTargets(40f)
        }
    }

    @Test
    fun `Given other layouts when rendering Enhanced then do not lower the AUTO header`() {
        val settings = enhancedConfig.copy(showEntityName = true, showActivity = true)
        for (state in listOf("heat", "cool", "off")) {
            val direct = views.enhancedTimeline(settings, thermostat(state), "°C", null, prefix, 234, 234)
            assertEquals(null, direct.loweredAutoHeader())
        }
        val entity = thermostat("heat_cool")
        assertEquals(null, compactTimeline(settings, entity).loweredAutoHeader())
        for (disabled in listOf(settings.copy(showEntityName = false), settings.copy(showActivity = false))) {
            val direct = views.enhancedTimeline(disabled, entity, "°C", null, prefix, 234, 234)
            assertEquals(null, direct.loweredAutoHeader())
        }
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
        ).texts()
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
        val texts = timeline.texts()
        assertTrue("18.0–22.0 °C" in texts)
        assertEquals(setOf("Down", "Up"), timeline.actions())
    }
}
