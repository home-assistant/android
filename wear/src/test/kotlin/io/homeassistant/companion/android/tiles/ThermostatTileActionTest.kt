package io.homeassistant.companion.android.tiles

import io.homeassistant.companion.android.common.data.integration.ClimateControls
import io.homeassistant.companion.android.database.wear.ThermostatLayoutStyle
import io.homeassistant.companion.android.database.wear.ThermostatTile
import io.homeassistant.companion.android.tiles.ThermostatCommandCoordinator.TemperatureSlot
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull

class ThermostatTileActionTest {
    private val prefix = "1|climate.living_room|"
    private val classic = ThermostatTile(id = 1)
    private val enhanced = classic.copy(layoutStyle = ThermostatLayoutStyle.ENHANCED)
    private val controls = mockk<ClimateControls> {
        every { hvacModes } returns listOf("heat", "cool", "heat_cool", "off")
        every { fanModes } returns listOf("auto", "on")
    }

    private fun resolve(action: String, config: ThermostatTile = enhanced, state: String = "heat") = resolveThermostatAction(prefix + action, prefix, config, state, controls)

    @Test
    fun `Given Classic when adjusted then route single and dual setpoints without calculating a baseline`() {
        assertEquals(ThermostatTileAction.Adjust(TemperatureSlot.CLASSIC_SINGLE, 1f), resolve("Up", classic))
        assertEquals(
            ThermostatTileAction.Adjust(TemperatureSlot.CLASSIC_RANGE, -1f),
            resolve("Down", classic, "heat_cool"),
        )
        assertNull(resolve("Up", classic, "off"))
        assertNull(resolve("Mode:off", classic))
        assertNull(resolve("Temp:single:1", classic))
    }

    @Test
    fun `Given Enhanced when adjusted then route individual temperature slots and utility actions`() {
        assertEquals(ThermostatTileAction.Adjust(TemperatureSlot.SINGLE, 1f), resolve("Temp:single:1"))
        assertEquals(ThermostatTileAction.Adjust(TemperatureSlot.HEAT, -1f), resolve("Temp:heat:-1"))
        assertEquals(ThermostatTileAction.Adjust(TemperatureSlot.COOL, 1f), resolve("Temp:cool:1"))
        assertEquals(ThermostatTileAction.Mode("off"), resolve("Mode:off"))
        assertNull(resolve("CycleFan"))
        assertEquals(ThermostatTileAction.Fan, resolve("CycleFan", enhanced.copy(showFanControl = true)))
    }

    @Test
    fun `Given stale target or unit prefix when clicked then reject the gesture`() {
        assertNull(resolveThermostatAction("2|climate.living_room|Temp:single:1", prefix, enhanced, "heat", controls))
        assertNull(resolveThermostatAction("1|climate.other|Temp:single:1", prefix, enhanced, "heat", controls))
        assertNull(resolveThermostatAction(prefix + "Temp:single:1", "$prefix°C:", enhanced, "heat", controls))
        assertNull(resolve("°F:Temp:single:1"))
    }

    @Test
    fun `Given hidden controls or unavailable state when clicked then reject the gesture`() {
        assertNull(resolve("Mode:off", enhanced.copy(showHvacModes = false)))
        assertNull(resolve("Mode:dry"))
        assertNull(resolve("CycleFan", enhanced.copy(showFanControl = false)))
        every { controls.fanModes } returns emptyList()
        assertNull(resolve("CycleFan", enhanced.copy(showFanControl = true)))
        assertNull(resolve("Temp:single:1", state = "unavailable"))
        assertNull(resolve("Mode:heat", state = "unknown"))
        assertNull(resolveThermostatAction(prefix + "CycleFan", prefix, enhanced, "heat", null))
    }

    @Test
    fun `Given secondary adjustment settings when clicked then accept only displayed step sizes`() {
        val secondary = enhanced.copy(showSecondaryButtons = true, secondaryStep = 3)
        assertEquals(ThermostatTileAction.Adjust(TemperatureSlot.SINGLE, -3f), resolve("Temp:single:-3", secondary))
        assertNull(resolve("Temp:single:3"))
        assertNull(resolve("Temp:single:5", secondary))
        assertNull(resolve("Temp:single:NaN", secondary))
        assertNull(resolve("Temp:single:1:extra", secondary))
    }
}
