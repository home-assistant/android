package io.homeassistant.companion.android.util.compose.entity

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.content.res.Configuration.UI_MODE_TYPE_NORMAL
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayItem
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState

@Preview(name = "phoneLTR", device = "spec:width=411.4dp,height=923.4dp", group = "phone") // Pixel 9 LTR
@Preview(name = "phoneRLT", device = "spec:width=411.4dp,height=923.4dp", group = "phone", locale = "ar") // Pixel 9 RTL
@Preview(
    name = "tablet",
    device = "spec:width=1280dp,height=800dp,dpi=320,orientation=portrait",
    group = "tablet",
    uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL,
)
private annotation class EntityPickerPreviews

class EntityPickerScreenshotTest {

    @PreviewTest
    @EntityPickerPreviews
    @Composable
    fun `EntityPicker loading`() {
        HAThemeForPreview {
            Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4)) {
                EntityPicker(
                    displayState = EntityDisplayState.Loading,
                    selectedEntityId = "light.bed",
                    onSelectionChanged = {},
                )
                EntityPicker(
                    displayState = EntityDisplayState.Loading,
                    selectedEntityId = null,
                    onSelectionChanged = {},
                    state = rememberEntityPickerState(isExpanded = true),
                )
            }
        }
    }

    @PreviewTest
    @EntityPickerPreviews
    @Composable
    fun `EntityPicker collapsed`() {
        HAThemeForPreview {
            Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4)) {
                EntityPicker(
                    displayState = EntityDisplayState.Loaded(createTestEntities()),
                    selectedEntityId = null,
                    onSelectionChanged = {},
                )
                EntityPicker(
                    displayState = EntityDisplayState.Loaded(createTestEntities()),
                    selectedEntityId = "light.bed",
                    onSelectionChanged = {},
                )
                EntityPicker(
                    displayState = EntityDisplayState.Loaded(createTestEntities()),
                    selectedEntityId = "sensor.temperature",
                    onSelectionChanged = {},
                )
            }
        }
    }

    @PreviewTest
    @EntityPickerPreviews
    @Composable
    fun `EntityPicker expanded with entities`() {
        HAThemeForPreview {
            EntityPicker(
                displayState = EntityDisplayState.Loaded(createTestEntities()),
                selectedEntityId = null,
                onSelectionChanged = {},
                state = rememberEntityPickerState(isExpanded = true),
                modifier = Modifier.padding(HADimens.SPACE4),
            )
        }
    }

    @PreviewTest
    @EntityPickerPreviews
    @Composable
    fun `EntityPicker expanded with selected entity`() {
        HAThemeForPreview {
            EntityPicker(
                displayState = EntityDisplayState.Loaded(createTestEntities()),
                selectedEntityId = "switch.fan",
                onSelectionChanged = {},
                state = rememberEntityPickerState(isExpanded = true),
                modifier = Modifier.padding(HADimens.SPACE4),
            )
        }
    }

    @PreviewTest
    @EntityPickerPreviews
    @Composable
    fun `EntityPicker with empty list`() {
        HAThemeForPreview {
            EntityPicker(
                displayState = EntityDisplayState.Loaded(emptyList()),
                selectedEntityId = null,
                onSelectionChanged = {},
                state = rememberEntityPickerState(isExpanded = true),
                modifier = Modifier.padding(HADimens.SPACE4),
            )
        }
    }

    @PreviewTest
    @EntityPickerPreviews
    @Composable
    fun `EntityPicker with many entities`() {
        HAThemeForPreview {
            EntityPicker(
                displayState = EntityDisplayState.Loaded(createManyTestEntities()),
                selectedEntityId = null,
                onSelectionChanged = {},
                state = rememberEntityPickerState(isExpanded = true),
                modifier = Modifier.padding(HADimens.SPACE4),
            )
        }
    }

    private fun createTestEntities() = listOf(
        EntityDisplayItem(
            entityId = "light.bed",
            name = "Bed Light",
            icon = CommunityMaterial.Icon2.cmd_lightbulb,
            areaName = "Bedroom",
            deviceName = "Device #1",
        ),
        EntityDisplayItem(
            entityId = "sensor.temperature",
            name = "Temperature",
            areaName = "Living Room",
            icon = CommunityMaterial.Icon3.cmd_temperature_celsius,
        ),
        EntityDisplayItem(
            entityId = "switch.fan",
            name = "Fan",
            icon = CommunityMaterial.Icon2.cmd_fan,
            areaName = "Bedroom",
            deviceName = "Device #2",
        ),
    )

    private fun createManyTestEntities() = listOf(
        EntityDisplayItem(
            entityId = "light.living_room",
            name = "Living Room Light",
            icon = CommunityMaterial.Icon2.cmd_lightbulb,
            areaName = "Living Room",
            deviceName = "Smart Bulb Pro",
        ),
        EntityDisplayItem(
            entityId = "light.bedroom",
            name = "Bedroom Light",
            icon = CommunityMaterial.Icon2.cmd_lightbulb,
            areaName = "Bedroom",
            deviceName = "Smart Bulb Basic",
        ),
        EntityDisplayItem(
            entityId = "light.kitchen",
            name = "Kitchen Light",
            icon = CommunityMaterial.Icon2.cmd_lightbulb,
            areaName = "Kitchen",
        ),
        EntityDisplayItem(
            entityId = "sensor.temperature",
            name = "Temperature Sensor",
            areaName = "Living Room",
            icon = CommunityMaterial.Icon3.cmd_temperature_celsius,
        ),
        EntityDisplayItem(
            entityId = "sensor.humidity",
            name = "Humidity Sensor",
            areaName = "Bathroom",
            icon = CommunityMaterial.Icon3.cmd_water_percent,
        ),
        EntityDisplayItem(
            entityId = "switch.fan",
            name = "Ceiling Fan",
            icon = CommunityMaterial.Icon2.cmd_fan,
            areaName = "Bedroom",
            deviceName = "Smart Switch",
        ),
        EntityDisplayItem(
            entityId = "switch.heater",
            name = "Heater",
            icon = CommunityMaterial.Icon3.cmd_radiator,
            areaName = "Living Room",
        ),
        EntityDisplayItem(
            entityId = "climate.thermostat",
            name = "Thermostat",
            icon = CommunityMaterial.Icon3.cmd_thermostat,
            areaName = "Hallway",
            deviceName = "Nest Thermostat",
        ),
        EntityDisplayItem(
            entityId = "lock.front_door",
            name = "Front Door Lock",
            icon = CommunityMaterial.Icon2.cmd_lock,
            areaName = "Entry",
            deviceName = "Smart Lock Pro",
        ),
        EntityDisplayItem(
            entityId = "cover.garage_door",
            name = "Garage Door",
            icon = CommunityMaterial.Icon2.cmd_garage,
            areaName = "Garage",
        ),
    )
}
