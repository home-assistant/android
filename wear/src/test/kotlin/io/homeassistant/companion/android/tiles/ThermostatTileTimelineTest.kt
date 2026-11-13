package io.homeassistant.companion.android.tiles

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.wear.protolayout.LayoutElementBuilders.Arc
import androidx.wear.protolayout.LayoutElementBuilders.ArcAdapter
import androidx.wear.protolayout.LayoutElementBuilders.ArcText
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Text
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.data.integration.ClimateControls
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplay
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithoutContext
import io.mockk.mockk
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class ThermostatTileTimelineTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun thermostat(rawState: String, hvacAction: String?) = EntityDisplayWithoutContext(
        entityId = "climate.living_room",
        name = "Living Room",
        icon = mockk(),
        rawState = rawState,
        climateControls = ClimateControls(
            currentTemperature = 19.4f,
            targetTemperature = 19.5f,
            targetTemperatureStep = 0.5f,
            hvacAction = hvacAction,
            minTemperature = 5f,
            maxTemperature = 30f,
            temperatureUnit = "°C",
            hvacModes = listOf("off", "heat"),
            supportsTargetTemperature = true,
        ),
    )

    private fun layout(
        entity: EntityDisplay,
        entityName: String? = null,
        targetTemperature: Float? = 19.5f,
    ): List<LayoutElement> = ThermostatTile.timeline(context, entity, entityName, targetTemperature, "°C")
        .timelineEntries.first().layout!!.root!!.withDescendants()

    private fun LayoutElement.withDescendants(): List<LayoutElement> = listOf(this) + children().flatMap {
        it.withDescendants()
    }

    private fun LayoutElement.children(): List<LayoutElement> = when (this) {
        is Box -> contents
        is Column -> contents
        is Row -> contents
        is Arc -> contents.filterIsInstance<ArcAdapter>().mapNotNull { it.content }
        else -> emptyList()
    }

    private fun List<LayoutElement>.tappableIds(): Set<String> = mapNotNull { (it as? Box)?.modifiers?.clickable?.id }
        .filter { it.isNotEmpty() }
        .toSet()

    private fun List<LayoutElement>.texts(): List<String> = mapNotNull { (it as? Text)?.text?.value }

    private fun List<LayoutElement>.arcTexts(): List<String> = filterIsInstance<Arc>()
        .flatMap { it.contents.filterIsInstance<ArcText>() }
        .mapNotNull { it.text?.value }

    @Test
    fun `Given a heating thermostat when building the tile then the power and temperature buttons can be tapped`() {
        val elements = layout(thermostat(rawState = "heat", hvacAction = "heating"))

        val ids = elements.tappableIds()

        assertTrue(ids.containsAll(setOf("Down", "Toggle", "Up")))
    }

    @Test
    fun `Given a thermostat that is off when building the tile then only the power button can be tapped`() {
        val elements = layout(thermostat(rawState = "off", hvacAction = "off"))

        val ids = elements.tappableIds()

        assertTrue("Toggle" in ids)
        assertFalse("Down" in ids)
        assertFalse("Up" in ids)
    }

    @Test
    fun `Given an unavailable thermostat when building the tile then no control can be tapped`() {
        val elements = layout(thermostat(rawState = "unavailable", hvacAction = null))

        val ids = elements.tappableIds()

        assertFalse("Toggle" in ids)
        assertFalse("Down" in ids)
        assertFalse("Up" in ids)
    }

    @Test
    fun `Given a heating thermostat when building the tile then it shows its action and temperatures`() {
        val elements = layout(thermostat(rawState = "heat", hvacAction = "heating"))

        val texts = elements.texts()

        assertTrue("Heating" in texts)
        assertTrue("19.5 °C" in texts)
        assertTrue("19.4 °C" in texts)
    }

    @Test
    fun `Given no target temperature when building the tile then dashes are shown for it`() {
        val elements = layout(thermostat(rawState = "off", hvacAction = "off"), targetTemperature = null)

        val texts = elements.texts()

        assertTrue("-- °C" in texts)
    }

    @Test
    fun `Given an entity name when building the tile then it is shown on the tile`() {
        val elements = layout(thermostat(rawState = "heat", hvacAction = "heating"), entityName = "Living Room")

        assertEquals(listOf("Living Room"), elements.arcTexts())
    }

    @Test
    fun `Given no entity name when building the tile then no name is shown on the tile`() {
        val elements = layout(thermostat(rawState = "heat", hvacAction = "heating"), entityName = null)

        assertTrue(elements.arcTexts().isEmpty())
    }
}
