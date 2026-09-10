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
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.Fan
import io.github.timoptr.mdiicons.generated.Garage
import io.github.timoptr.mdiicons.generated.Lightbulb
import io.github.timoptr.mdiicons.generated.Lock
import io.github.timoptr.mdiicons.generated.Radiator
import io.github.timoptr.mdiicons.generated.TemperatureCelsius
import io.github.timoptr.mdiicons.generated.Thermostat
import io.github.timoptr.mdiicons.generated.WaterPercent
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithContext
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithoutContext

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
        EntityDisplayWithContext(
            item = EntityDisplayWithoutContext(
                entityId = "light.bed",
                name = "Bed Light",
                icon = Mdi.Lightbulb,
            ),
            areaName = "Bedroom",
            deviceName = "Device #1",
        ),
        EntityDisplayWithContext(
            item = EntityDisplayWithoutContext(
                entityId = "sensor.temperature",
                name = "Temperature",
                icon = Mdi.TemperatureCelsius,
            ),
            areaName = "Living Room",
        ),
        EntityDisplayWithContext(
            item = EntityDisplayWithoutContext(
                entityId = "switch.fan",
                name = "Fan",
                icon = Mdi.Fan,
            ),
            areaName = "Bedroom",
            deviceName = "Device #2",
        ),
    )

    private fun createManyTestEntities() = listOf(
        EntityDisplayWithContext(
            item = EntityDisplayWithoutContext(
                entityId = "light.attic",
                name = "Attic Light",
                icon = Mdi.Lightbulb,
                isHidden = true,
            ),
            areaName = "Attic",
        ),
        EntityDisplayWithContext(
            item = EntityDisplayWithoutContext(
                entityId = "light.living_room",
                name = "Living Room Light",
                icon = Mdi.Lightbulb,
            ),

            areaName = "Living Room",
            deviceName = "Smart Bulb Pro",
        ),
        EntityDisplayWithContext(
            item = EntityDisplayWithoutContext(
                entityId = "light.bedroom",
                name = "Bedroom Light",
                icon = Mdi.Lightbulb,
            ),
            areaName = "Bedroom",
            deviceName = "Smart Bulb Basic",
        ),
        EntityDisplayWithContext(
            item = EntityDisplayWithoutContext(
                entityId = "light.kitchen",
                name = "Kitchen Light",
                icon = Mdi.Lightbulb,
            ),
            areaName = "Kitchen",
        ),
        EntityDisplayWithContext(
            item = EntityDisplayWithoutContext(
                entityId = "sensor.temperature",
                name = "Temperature Sensor",
                icon = Mdi.TemperatureCelsius,
            ),
            areaName = "Living Room",
        ),
        EntityDisplayWithContext(
            item = EntityDisplayWithoutContext(
                entityId = "sensor.humidity",
                name = "Humidity Sensor",
                icon = Mdi.WaterPercent,
            ),
            areaName = "Bathroom",
        ),
        EntityDisplayWithContext(
            item = EntityDisplayWithoutContext(
                entityId = "switch.fan",
                name = "Ceiling Fan",
                icon = Mdi.Fan,
            ),
            areaName = "Bedroom",
            deviceName = "Smart Switch",
        ),
        EntityDisplayWithContext(
            item = EntityDisplayWithoutContext(
                entityId = "switch.heater",
                name = "Heater",
                icon = Mdi.Radiator,
            ),

            areaName = "Living Room",
        ),
        EntityDisplayWithContext(
            item = EntityDisplayWithoutContext(
                entityId = "climate.thermostat",
                name = "Thermostat",
                icon = Mdi.Thermostat,
            ),

            areaName = "Hallway",
            deviceName = "Nest Thermostat",
        ),
        EntityDisplayWithContext(
            item = EntityDisplayWithoutContext(
                entityId = "lock.front_door",
                name = "Front Door Lock",
                icon = Mdi.Lock,
            ),

            areaName = "Entry",
            deviceName = "Smart Lock Pro",
        ),
        EntityDisplayWithContext(
            item = EntityDisplayWithoutContext(
                entityId = "cover.garage_door",
                name = "Garage Door",
                icon = Mdi.Garage,
            ),
            areaName = "Garage",
        ),
    )
}
