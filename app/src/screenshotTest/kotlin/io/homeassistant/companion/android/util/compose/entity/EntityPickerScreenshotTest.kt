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
    fun `EntityPicker collapsed`() {
        HAThemeForPreview {
            Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4)) {
                EntityPicker(
                    entities = createTestEntities(),
                    selectedEntityId = null,
                    onEntitySelectedId = {},
                    onEntityCleared = {},
                )
                EntityPicker(
                    entities = createTestEntities(),
                    selectedEntityId = "light.bed",
                    onEntitySelectedId = {},
                    onEntityCleared = {},
                )
                EntityPicker(
                    entities = createTestEntities(),
                    selectedEntityId = "sensor.temperature",
                    onEntitySelectedId = {},
                    onEntityCleared = {},
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
                entities = createTestEntities(),
                selectedEntityId = null,
                onEntitySelectedId = {},
                onEntityCleared = {},
                isExpanded = true,
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
                entities = createTestEntities(),
                selectedEntityId = "switch.fan",
                onEntitySelectedId = {},
                onEntityCleared = {},
                isExpanded = true,
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
                entities = emptyList(),
                selectedEntityId = null,
                onEntitySelectedId = {},
                onEntityCleared = {},
                isExpanded = true,
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
                entities = createManyTestEntities(),
                selectedEntityId = null,
                onEntitySelectedId = {},
                onEntityCleared = {},
                isExpanded = true,
                modifier = Modifier.padding(HADimens.SPACE4),
            )
        }
    }

    private fun createTestEntities() = listOf(
        EntityPickerItem(
            entityId = "light.bed",
            domain = "light",
            friendlyName = "Bed Light",
            icon = CommunityMaterial.Icon2.cmd_lightbulb,
            areaName = "Bedroom",
            deviceName = "Device #1",
        ),
        EntityPickerItem(
            entityId = "sensor.temperature",
            domain = "sensor",
            friendlyName = "Temperature",
            areaName = "Living Room",
            icon = CommunityMaterial.Icon3.cmd_temperature_celsius,
        ),
        EntityPickerItem(
            entityId = "switch.fan",
            domain = "switch",
            friendlyName = "Fan",
            icon = CommunityMaterial.Icon2.cmd_fan,
            areaName = "Bedroom",
            deviceName = "Device #2",
        ),
    )

    private fun createManyTestEntities() = listOf(
        EntityPickerItem(
            entityId = "light.living_room",
            domain = "light",
            friendlyName = "Living Room Light",
            icon = CommunityMaterial.Icon2.cmd_lightbulb,
            areaName = "Living Room",
            deviceName = "Smart Bulb Pro",
        ),
        EntityPickerItem(
            entityId = "light.bedroom",
            domain = "light",
            friendlyName = "Bedroom Light",
            icon = CommunityMaterial.Icon2.cmd_lightbulb,
            areaName = "Bedroom",
            deviceName = "Smart Bulb Basic",
        ),
        EntityPickerItem(
            entityId = "light.kitchen",
            domain = "light",
            friendlyName = "Kitchen Light",
            icon = CommunityMaterial.Icon2.cmd_lightbulb,
            areaName = "Kitchen",
        ),
        EntityPickerItem(
            entityId = "sensor.temperature",
            domain = "sensor",
            friendlyName = "Temperature Sensor",
            areaName = "Living Room",
            icon = CommunityMaterial.Icon3.cmd_temperature_celsius,
        ),
        EntityPickerItem(
            entityId = "sensor.humidity",
            domain = "sensor",
            friendlyName = "Humidity Sensor",
            areaName = "Bathroom",
            icon = CommunityMaterial.Icon3.cmd_water_percent,
        ),
        EntityPickerItem(
            entityId = "switch.fan",
            domain = "switch",
            friendlyName = "Ceiling Fan",
            icon = CommunityMaterial.Icon2.cmd_fan,
            areaName = "Bedroom",
            deviceName = "Smart Switch",
        ),
        EntityPickerItem(
            entityId = "switch.heater",
            domain = "switch",
            friendlyName = "Heater",
            icon = CommunityMaterial.Icon3.cmd_radiator,
            areaName = "Living Room",
        ),
        EntityPickerItem(
            entityId = "climate.thermostat",
            domain = "climate",
            friendlyName = "Thermostat",
            icon = CommunityMaterial.Icon3.cmd_thermostat,
            areaName = "Hallway",
            deviceName = "Nest Thermostat",
        ),
        EntityPickerItem(
            entityId = "lock.front_door",
            domain = "lock",
            friendlyName = "Front Door Lock",
            icon = CommunityMaterial.Icon2.cmd_lock,
            areaName = "Entry",
            deviceName = "Smart Lock Pro",
        ),
        EntityPickerItem(
            entityId = "cover.garage_door",
            domain = "cover",
            friendlyName = "Garage Door",
            icon = CommunityMaterial.Icon2.cmd_garage,
            areaName = "Garage",
        ),
    )
}
