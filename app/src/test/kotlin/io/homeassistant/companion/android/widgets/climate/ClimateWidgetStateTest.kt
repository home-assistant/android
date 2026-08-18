package io.homeassistant.companion.android.widgets.climate

import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial.Icon
import io.homeassistant.companion.android.common.data.integration.ClimateControls
import io.homeassistant.companion.android.common.data.integration.HvacMode
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithoutContext
import io.homeassistant.companion.android.database.widget.ClimateWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClimateWidgetStateTest {

    @Test
    fun `Given ClimateWidgetEntity and Entity when invoking from then returns ClimateStateWithData with synced data`() {
        val climateEntity = ClimateWidgetEntity(
            id = 42,
            backgroundType = WidgetBackgroundType.DAYNIGHT,
            textColor = "#FFFFFF",
            serverId = 1,
            entityId = "climate.samsung",
        )
        val displayEntity = fakeEntityDisplay("climate.samsung", "Samsung HVAC").copy(
            climateControls = ClimateControls(
                currentTemperature = 22f,
                targetTemperature = 24f,
                targetTemperatureStep = 1f,
                minTemperature = 1f,
                maxTemperature = 1f,
                hvacAction = "heat",
                hvacSupportedModes = listOf("off", "heat", "cool")
            ),
            rawState = "heat",
        )

        val result = ClimateStateWithData.from(climateEntity, displayEntity)

        assertEquals(WidgetBackgroundType.DAYNIGHT, result.backgroundType)
        assertEquals("#FFFFFF", result.textColor)
        assertEquals(1, result.serverId)
        assertEquals("climate.samsung", result.listEntityId)
        assertEquals("Samsung HVAC", result.climateName)
        assertEquals(22f, result.currentTemp)
        assertEquals(24f, result.climateTemp)
        assertEquals(HvacMode.HEAT, result.hvacSelectedMode)
        assertFalse(result.outOfSync)
    }

    @Test
    fun `Given ClimateWidgetEntity with latest update data when invoking from then returns ClimateStateWithData with outOfSync true`() {
        val climateEntity = ClimateWidgetEntity(
            id = 42,
            backgroundType = WidgetBackgroundType.DAYNIGHT,
            textColor = "#FFFFFF",
            serverId = 2,
            entityId = "climate.samsung",
            latestUpdateData = ClimateWidgetEntity.LastUpdateData(
                entityName = "Samsung HVAC",
                climateTemp = 24f,
                currentTemp = 22f,
                minTemp = 15f,
                maxTemp = 30f,
                stepTemp = 1f,
                stateClimate = "heat",
                hvacModesSupported = listOf("off", "heat"),
            ),
        )

        val result = ClimateStateWithData.from(climateEntity)

        assertEquals(WidgetBackgroundType.DAYNIGHT, result.backgroundType)
        assertEquals("#FFFFFF", result.textColor)
        assertEquals(2, result.serverId)
        assertEquals("climate.samsung", result.listEntityId)
        assertEquals("Samsung HVAC", result.climateName)
        assertEquals(24f, result.climateTemp)
        assertEquals(22f, result.currentTemp)
        assertEquals(HvacMode.HEAT, result.hvacSelectedMode)
        assertEquals(listOf(HvacMode.OFF, HvacMode.HEAT), result.hvacSupportedModes)
        assertTrue(result.outOfSync)
    }

    @Test
    fun `Given ClimateWidgetEntity without latest update data when invoking from then returns empty outOfSync state`() {
        val climateEntity = ClimateWidgetEntity(
            id = 42,
            backgroundType = WidgetBackgroundType.DAYNIGHT,
            textColor = "#FFFFFF",
            serverId = 1,
            entityId = "climate.samsung",
        )

        val result = ClimateStateWithData.from(climateEntity)

        assertEquals(WidgetBackgroundType.DAYNIGHT, result.backgroundType)
        assertEquals("#FFFFFF", result.textColor)
        assertEquals(1, result.serverId)
        assertEquals("climate.samsung", result.listEntityId)
        assertEquals("", result.climateName)
        assertNull(result.currentTemp)
        assertNull(result.climateTemp)
        assertNull(result.hvacSelectedMode)
        assertTrue(result.hvacSupportedModes.isEmpty())
        assertTrue(result.outOfSync)
    }

    @Test
    fun `Given Entity without optional climate controls when invoking from then returns ClimateStateWithData with null values`() {
        val climateEntity = ClimateWidgetEntity(
            id = 42,
            backgroundType = WidgetBackgroundType.DAYNIGHT,
            textColor = "#FFFFFF",
            serverId = 1,
            entityId = "climate.samsung",
        )

        val displayEntity = fakeEntityDisplay("climate.samsung", "Samsung HVAC").copy(
            climateControls = ClimateControls(
                currentTemperature = null,
                targetTemperature = null,
                targetTemperatureStep = null,
                minTemperature = null,
                maxTemperature = null,
                hvacAction = null,
                hvacSupportedModes = emptyList(),
            ),
            rawState = "unknown",
        )

        val result = ClimateStateWithData.from(climateEntity, displayEntity)

        assertEquals("Samsung HVAC", result.climateName)
        assertNull(result.currentTemp)
        assertNull(result.climateTemp)
        assertNull(result.hvacSelectedMode)
        assertTrue(result.hvacSupportedModes.isEmpty())
        assertFalse(result.outOfSync)
    }

    private fun fakeEntityDisplay(entityId: String, name: String, icon: IIcon? = null): EntityDisplayWithoutContext {
        return EntityDisplayWithoutContext(entityId, name, icon ?: Icon.cmd_bookmark)
    }
}
