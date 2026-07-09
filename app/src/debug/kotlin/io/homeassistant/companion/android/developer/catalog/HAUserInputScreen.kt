package io.homeassistant.companion.android.developer.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Devices.TABLET
import androidx.compose.ui.tooling.preview.Preview
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.compose.composable.HACheckbox
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.composable.HADropdownMenu
import io.homeassistant.companion.android.common.compose.composable.HARadioGroup
import io.homeassistant.companion.android.common.compose.composable.HASearchField
import io.homeassistant.companion.android.common.compose.composable.HASwitch
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.composable.RadioOption
import io.homeassistant.companion.android.common.compose.composable.rememberSearchFieldState
import io.homeassistant.companion.android.common.compose.composable.rememberSelectedDropdownKey
import io.homeassistant.companion.android.common.compose.composable.rememberSelectedOption
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayItem
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.util.compose.entity.EntityPicker
import java.time.LocalDateTime

fun LazyListScope.catalogUserInputSection() {
    input()
    dropdownMenu()
    entityPicker()
    switches()
    checkboxes()
    radioGroupSection()
}

private fun LazyListScope.input() {
    catalogSection(title = "Input") {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            var value1 by remember { mutableStateOf("") }
            var value2 by remember { mutableStateOf("") }
            var value3 by remember { mutableStateOf("") }
            var value4 by remember { mutableStateOf("") }
            var value5 by remember { mutableStateOf("error") }
            var value6 by remember { mutableStateOf("super secret") }
            val searchState = rememberSearchFieldState()
            CatalogRow {
                HATextField(
                    value = value1,
                    onValueChange = { value1 = it },
                    trailingIcon = {
                        if (value1.isNotBlank()) {
                            IconButton(onClick = { value1 = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                )
                            }
                        }
                    },
                )
                HATextField(
                    value = value2,
                    onValueChange = { value2 = it },
                    placeholder = {
                        Text(
                            text = "Placeholder",
                            style = HATextStyle.UserInput.copy(color = Color.Unspecified),
                        )
                    },
                )
                HATextField(
                    value = value3,
                    onValueChange = { value3 = it },
                    label = {
                        Text(
                            text = "Label",
                            style = HATextStyle.UserInput.copy(color = Color.Unspecified),
                        )
                    },
                )
                HATextField(
                    value = value4,
                    onValueChange = { value4 = it },
                    label = {
                        Text(
                            text = "Label",
                            style = HATextStyle.UserInput.copy(color = Color.Unspecified),
                        )
                    },
                    placeholder = {
                        Text(
                            text = "Placeholder",
                            style = HATextStyle.UserInput.copy(color = Color.Unspecified),
                        )
                    },
                )
                HATextField(
                    value = value5,
                    onValueChange = { value5 = it },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                        )
                    },
                    label = {
                        Text(
                            text = "Label",
                            style = HATextStyle.UserInput.copy(color = Color.Unspecified),
                        )
                    },
                    placeholder = {
                        Text(
                            text = "Placeholder",
                            style = HATextStyle.UserInput.copy(color = Color.Unspecified),
                        )
                    },
                    isError = value5.isNotBlank(),
                    supportingText = {
                        if (value5.isNotBlank()) {
                            Text(
                                text = "Supporting text",
                                style = HATextStyle.BodyMedium.copy(color = Color.Unspecified),
                            )
                        }
                    },
                )
                HATextField(
                    value = BIG_CONTENT,
                    enabled = false,
                    onValueChange = { },
                    label = {
                        Text(
                            text = "Label",
                            style = HATextStyle.UserInput.copy(color = Color.Unspecified),
                        )
                    },
                    placeholder = {
                        Text(
                            text = "Placeholder",
                            style = HATextStyle.UserInput.copy(color = Color.Unspecified),
                        )
                    },
                )
                HATextField(
                    value = value6,
                    onValueChange = { value6 = it },
                    visualTransformation = PasswordVisualTransformation(),
                    label = {
                        Text(
                            text = "Password",
                            style = HATextStyle.UserInput.copy(color = Color.Unspecified),
                        )
                    },
                    placeholder = {
                        Text(
                            text = "Placeholder",
                            style = HATextStyle.UserInput.copy(color = Color.Unspecified),
                        )
                    },
                )
                HASearchField(state = searchState)
            }
        }
    }
}

private fun LazyListScope.radioGroupSection() {
    catalogSection(title = "Radio group") {
        var selectedOption by rememberSelectedOption<String>()

        HARadioGroup(
            options = listOf(
                RadioOption(
                    "key1",
                    "Title",
                    "SubTitle",
                ),
                RadioOption(
                    "key2",
                    "Title2",
                ),
                RadioOption(
                    "key3",
                    "Title2",
                    enabled = false,
                ),
                RadioOption(
                    "key3",
                    "Very long text, to verifiy that nothing is broken when it is displayed within the bounds.",
                    enabled = false,
                ),
            ),
            onSelect = {
                selectedOption = it
            },
            selectionKey = selectedOption?.selectionKey,
        )
    }
}

private fun LazyListScope.switches() {
    catalogSection(title = "Switches") {
        CatalogRow {
            var isChecked by remember { mutableStateOf(false) }
            HASwitch(
                checked = isChecked,
                onCheckedChange = {
                    isChecked = it
                },
            )
            HASwitch(
                checked = !isChecked,
                onCheckedChange = {
                    isChecked = !it
                },
            )
        }
    }
}

private fun LazyListScope.checkboxes() {
    catalogSection(title = "Checkboxes") {
        CatalogRow {
            var isChecked by remember { mutableStateOf(false) }
            HACheckbox(
                checked = isChecked,
                onCheckedChange = { isChecked = it },
            )
            HACheckbox(
                checked = !isChecked,
                onCheckedChange = { isChecked = !it },
            )
            HACheckbox(
                checked = true,
                onCheckedChange = null,
                enabled = false,
            )
            HACheckbox(
                checked = false,
                onCheckedChange = null,
                enabled = false,
            )
        }
    }
}

private fun LazyListScope.dropdownMenu() {
    catalogSection(title = "Dropdown Menu") {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            var selectedKey1 by rememberSelectedDropdownKey<Int>()
            var selectedKey2 by rememberSelectedDropdownKey(3)

            CatalogRow {
                HADropdownMenu(
                    items = sampleDropdownItems,
                    selectedKey = selectedKey2,
                    onItemSelected = {},
                    label = "Server",
                    enabled = false,
                )
                HADropdownMenu(
                    items = sampleDropdownItems,
                    selectedKey = selectedKey1,
                    onItemSelected = { selectedKey1 = it },
                    label = "Server",
                )
            }
        }
    }
}

private fun LazyListScope.entityPicker() {
    catalogSection(title = "Entity Pickers") {
        var selectedEntityId by remember { mutableStateOf<String?>(null) }

        EntityPicker(
            displayState = EntityDisplayState.Loaded(sampleDisplayEntities),
            selectedEntityId = selectedEntityId,
            onSelectionChanged = { selectedEntityId = it },
        )
    }
    catalogSection(title = "Entity Picker loading") {
        EntityPicker(
            displayState = EntityDisplayState.Loading,
            selectedEntityId = "light.living_room",
            onSelectionChanged = {},
        )
    }
    catalogSection(title = "Entity Picker error") {
        EntityPicker(
            displayState = EntityDisplayState.Error,
            selectedEntityId = "light.living_room",
            onSelectionChanged = {},
        )
    }
}

private val now = LocalDateTime.now()

private val sampleDropdownItems = (1..30).map { index ->
    HADropdownItem(key = index, label = "Server $index")
}

private val sampleDisplayEntities = listOf(
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
    ),
    EntityDisplayItem(
        entityId = "sensor.temperature",
        name = "Temperature Sensor",
        icon = CommunityMaterial.Icon3.cmd_thermometer,
    ),
    EntityDisplayItem(
        entityId = "switch.fan",
        name = "Ceiling Fan",
        icon = CommunityMaterial.Icon2.cmd_fan,
    ),
    EntityDisplayItem(
        entityId = "binary_sensor.motion",
        name = "Motion Sensor",
        icon = CommunityMaterial.Icon3.cmd_motion_sensor,
    ),
    EntityDisplayItem(
        entityId = "cover.garage_door",
        name = "Garage Door",
        icon = CommunityMaterial.Icon2.cmd_garage,
    ),
)

@Preview(showBackground = true, device = TABLET)
@Composable
private fun PreviewHAUserInputScreen() {
    HAThemeForPreview {
        LazyColumn {
            catalogUserInputSection()
        }
    }
}
