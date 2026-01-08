package io.homeassistant.companion.android.util.compose

import androidx.annotation.VisibleForTesting
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.ButtonSize
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.composable.HAModalBottomSheet
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.theme.HABorderWidth
import io.homeassistant.companion.android.common.compose.theme.HAColorScheme
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.compose.theme.MaxButtonWidth
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.friendlyName
import io.homeassistant.companion.android.util.icondialog.getIconByMdiName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Breakpoint for compact screens (phones). Screens wider than this are considered tablets. */
private val COMPACT_WIDTH_BREAKPOINT = 600.dp

/**
 * Data class representing an entity in the picker.
 *
 * @param entityId The unique identifier of the entity (e.g., "light.living_room")
 * @param friendlyName The human-readable name of the entity
 * @param icon The MDI icon name (e.g., "mdi:lightbulb") or null for default domain icon
 * @param areaName The optional area where the entity is located
 * @param deviceName The optional device name associated with the entity
 */
data class EntityPickerItem(
    val entityId: String,
    val friendlyName: String,
    val icon: String? = null,
    val areaName: String? = null,
    val deviceName: String? = null,
) {
    /**
     * Returns a formatted string combining area and device name.
     * Format: "Area name ▸ Device name" or just one if the other is null.
     */
    val subtitle: String?
        get() = listOfNotNull(areaName, deviceName)
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" ▸ ")

    /** The domain extracted from the entity ID (e.g., "light" from "light.living_room"). */
    val domain: String
        get() = entityId.substringBefore(".")
}

/**
 * Converts an [Entity] to an [EntityPickerItem].
 *
 * @param areaName The area name to display
 * @param deviceName The device name to display
 */
fun Entity.toPickerItem(areaName: String? = null, deviceName: String? = null): EntityPickerItem = EntityPickerItem(
    entityId = entityId,
    friendlyName = friendlyName,
    icon = attributes["icon"] as? String,
    areaName = areaName,
    deviceName = deviceName,
)

/** Default icon for domains without a specific fallback. */
private const val DEFAULT_DOMAIN_ICON = "mdi:bookmark"

/** Fallback icons for each domain when no icon is specified on the entity. */
@VisibleForTesting val FALLBACK_DOMAIN_ICONS = mapOf(
    "air_quality" to "mdi:air-filter",
    "alert" to "mdi:alert",
    "automation" to "mdi:robot",
    "calendar" to "mdi:calendar",
    "climate" to "mdi:thermostat",
    "configurator" to "mdi:cog",
    "conversation" to "mdi:forum-outline",
    "counter" to "mdi:counter",
    "date" to "mdi:calendar",
    "datetime" to "mdi:calendar-clock",
    "device_tracker" to "mdi:account",
    "fan" to "mdi:fan",
    "group" to "mdi:google-circles-communities",
    "homeassistant" to "mdi:home-assistant",
    "humidifier" to "mdi:air-humidifier",
    "image_processing" to "mdi:image-filter-frames",
    "image" to "mdi:image",
    "input_boolean" to "mdi:toggle-switch",
    "input_button" to "mdi:button-pointer",
    "input_datetime" to "mdi:calendar-clock",
    "input_number" to "mdi:ray-vertex",
    "input_select" to "mdi:format-list-bulleted",
    "input_text" to "mdi:form-textbox",
    "lawn_mower" to "mdi:robot-mower",
    "light" to "mdi:lightbulb",
    "lock" to "mdi:lock",
    "media_player" to "mdi:cast",
    "notify" to "mdi:comment-alert",
    "number" to "mdi:ray-vertex",
    "persistent_notification" to "mdi:bell",
    "person" to "mdi:account",
    "plant" to "mdi:flower",
    "remote" to "mdi:remote",
    "scene" to "mdi:palette",
    "schedule" to "mdi:calendar-clock",
    "script" to "mdi:script-text",
    "select" to "mdi:format-list-bulleted",
    "sensor" to "mdi:eye",
    "siren" to "mdi:bullhorn",
    "stt" to "mdi:microphone-message",
    "sun" to "mdi:white-balance-sunny",
    "switch" to "mdi:flash",
    "text" to "mdi:form-textbox",
    "time" to "mdi:clock",
    "timer" to "mdi:timer-outline",
    "todo" to "mdi:clipboard-list",
    "tts" to "mdi:speaker-message",
    "vacuum" to "mdi:robot-vacuum",
    "water_heater" to "mdi:water-boiler",
    "weather" to "mdi:weather-partly-cloudy",
    "zone" to "mdi:map-marker-radius",
)

/**
 * Returns the icon to display for this entity.
 * Uses the entity's icon if available, otherwise falls back to a domain-specific icon.
 */
// TODO more logic to support custom icons? No the icons are embedded in the frontend we don't have access to them
private fun EntityPickerItem.getDisplayIcon(): String {
    return icon ?: FALLBACK_DOMAIN_ICONS[domain] ?: DEFAULT_DOMAIN_ICON
}

/**
 * A picker component for selecting Home Assistant entities.
 *
 * This composable displays different states:
 * - When no entity is selected: Shows an "Add entity" button
 * - When expanded on small screens: Shows a bottom sheet with search and entity list
 * - When expanded on tablets: Shows an inline dropdown with search and entity list
 * - When an entity is selected: Shows a chip with the entity name and a close button
 *
 * @param entities The list of available entities to choose from
 * @param selectedEntity The currently selected entity, or null if none selected
 * @param onEntitySelected Callback invoked when an entity is selected
 * @param onEntityCleared Callback invoked when the selection is cleared
 * @param modifier The modifier to apply to this composable
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntityPicker(
    entities: List<EntityPickerItem>,
    selectedEntity: EntityPickerItem?,
    onEntitySelected: (EntityPickerItem) -> Unit,
    onEntityCleared: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val screenWidth = screenWidth()
    val isCompactScreen = screenWidth < COMPACT_WIDTH_BREAKPOINT

    var isExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    Column(modifier = modifier) {
        if (selectedEntity != null) {
            SelectedEntityChip(
                entity = selectedEntity,
                onClearClick = {
                    onEntityCleared()
                    searchQuery = ""
                },
                onExpandClick = { isExpanded = !isExpanded },
            )
        } else {
            HAFilledButton(
                text = stringResource(commonR.string.entity_picker_add_entity),
                onClick = { isExpanded = true },
                size = ButtonSize.SMALL,
                prefix = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
            )
        }

        if (isCompactScreen) {
            // Bottom sheet for small screens
            if (isExpanded) {
                HAModalBottomSheet(
                    bottomSheetState = bottomSheetState,
                    onDismissRequest = {
                        isExpanded = false
                        searchQuery = ""
                    },
                ) {
                    EntityPickerContent(
                        entities = entities,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        onEntitySelected = { entity ->
                            scope.launch {
                                bottomSheetState.hide()
                                onEntitySelected(entity)
                                isExpanded = false
                                searchQuery = ""
                            }
                        },
                    )
                }
            }
        } else {
            // Inline dropdown for tablets
            AnimatedVisibility(visible = isExpanded) {
                EntityPickerDropdown(
                    entities = entities,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    onEntitySelected = { entity ->
                        onEntitySelected(entity)
                        isExpanded = false
                        searchQuery = ""
                    },
                )
            }
        }
    }
}

/**
 * Displays the selected entity as a chip with name, subtitle, and close button.
 */
@Composable
private fun SelectedEntityChip(
    entity: EntityPickerItem,
    onClearClick: () -> Unit,
    onExpandClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = LocalHAColorScheme.current

    Row(
        modifier = Modifier
            .widthIn(max = MaxButtonWidth)
            .then(modifier)
            .fillMaxWidth()
            .enclosureBorder(colorScheme)
            .clickable(onClick = onExpandClick)
            .padding(HADimens.SPACE3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE3),
    ) {
        EntityContent(entity)
        IconButton(
            onClick = onClearClick,
            modifier = Modifier.size(HADimens.SPACE8),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(commonR.string.search_clear_selection),
                tint = colorScheme.colorOnNeutralNormal,
                modifier = Modifier.size(HADimens.SPACE6),
            )
        }
    }
}

@Composable
private fun RowScope.EntityContent(entity: EntityPickerItem) {
    val colorScheme = LocalHAColorScheme.current
    Image(
        asset = CommunityMaterial.getIconByMdiName(entity.getDisplayIcon())
            ?: CommunityMaterial.Icon.cmd_bookmark,
        colorFilter = ColorFilter.tint(colorScheme.colorTextSecondary),
        contentDescription = null,
        modifier = Modifier.size(HADimens.SPACE6),
    )
    Column(modifier = Modifier.weight(1f)) {
        Text(
            text = entity.friendlyName,
            style = HATextStyle.Body,
            color = colorScheme.colorTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        entity.subtitle?.let { subtitle ->
            Text(
                text = subtitle,
                style = HATextStyle.BodyMedium,
                color = colorScheme.colorTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Stable
private fun Modifier.enclosureBorder(colorScheme: HAColorScheme): Modifier {
    return this
        .clip(RoundedCornerShape(HARadius.XL))
        .border(
            width = HABorderWidth.S,
            color = colorScheme.colorBorderNeutralQuiet,
            shape = RoundedCornerShape(HARadius.XL),
        )
        .background(colorScheme.colorSurfaceDefault)
}

/**
 * The dropdown panel containing search field and entity list (used on tablets).
 */
@Composable
private fun EntityPickerDropdown(
    entities: List<EntityPickerItem>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onEntitySelected: (EntityPickerItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = LocalHAColorScheme.current

    Column(
        modifier = modifier
            // TODO maybe use shadow instead of border
            .enclosureBorder(colorScheme)
            // No horizontal padding to be able to have list header to take the whole space
            .padding(top = HADimens.SPACE3),
    ) {
        EntityPickerContent(
            entities = entities,
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            onEntitySelected = onEntitySelected,
        )
    }
}

/**
 * The shared content for the entity picker, used in both bottom sheet and dropdown.
 */
@Composable
private fun EntityPickerContent(
    entities: List<EntityPickerItem>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onEntitySelected: (EntityPickerItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = LocalHAColorScheme.current

    var filteredEntities by remember { mutableStateOf(entities) }

    // TODO move this out of the code into a higher level function to do more complex search
    //  mixin area, entity name, ... and fuzzy search
    LaunchedEffect(entities, searchQuery) {
        filteredEntities = withContext(Dispatchers.Default) {
            val query = searchQuery.trim()
            val filtered = if (query.isBlank()) {
                entities
            } else {
                entities.filter { entity ->
                    entity.friendlyName.contains(query, ignoreCase = true) ||
                        entity.entityId.contains(query.replace(" ", "_"), ignoreCase = true)
                }
            }

            filtered
                .sortedWith(
                    compareBy(
                        { !it.friendlyName.startsWith(query, ignoreCase = true) },
                        {
                            !it.entityId.split(".")
                                .getOrNull(1)
                                .orEmpty()
                                .startsWith(query.replace(" ", "_"), ignoreCase = true)
                        },
                        { it.friendlyName.lowercase() },
                    ),
                )
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(HADimens.SPACE3)) {
        HATextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            label = { Text(stringResource(commonR.string.search)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(commonR.string.clear_search),
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HADimens.SPACE3),
        )

        if (filteredEntities.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp),
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colorScheme.colorSurfaceLow),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(commonR.string.entities),
                            style = HATextStyle.Body,
                            color = colorScheme.colorTextSecondary,
                            modifier = Modifier.padding(
                                horizontal = HADimens.SPACE3,
                                vertical = HADimens.SPACE1,
                            ),
                        )
                    }
                }
                items(
                    items = filteredEntities,
                    key = { it.entityId },
                ) { entity ->
                    EntityListItem(
                        entity = entity,
                        onClick = { onEntitySelected(entity) },
                        modifier = Modifier.padding(horizontal = HADimens.SPACE3),
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(
                            start =
                            HADimens.SPACE12,
                        ),
                    )
                }
            }
        }
        // TODO add a placeholder when empty
    }
}

/**
 * A single entity item in the list.
 */
@Composable
private fun EntityListItem(entity: EntityPickerItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(HADimens.SPACE12)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
    ) {
        EntityContent(entity)
    }
}

@Preview
@Composable
private fun EntityPickerCollapsedPreview() {
    HAThemeForPreview {
        Box(modifier = Modifier.padding(HADimens.SPACE4)) {
            EntityPicker(
                entities = emptyList(),
                selectedEntity = null,
                onEntitySelected = {},
                onEntityCleared = {},
            )
        }
    }
}

@Preview
@Composable
private fun EntityPickerSelectedPreview() {
    HAThemeForPreview {
        Box(modifier = Modifier.padding(HADimens.SPACE4)) {
            EntityPicker(
                entities = emptyList(),
                selectedEntity = EntityPickerItem(
                    entityId = "light.living_room",
                    friendlyName = "Living Room Light",
                    icon = "mdi:ceiling-light",
                    areaName = "Living Room",
                    deviceName = "Philips Hue",
                ),
                onEntitySelected = {},
                onEntityCleared = {},
            )
        }
    }
}

@Preview
@Composable
private fun EntityPickerExpandedPreview() {
    HAThemeForPreview {
        val entities = listOf(
            EntityPickerItem(
                entityId = "light.bed",
                friendlyName = "Bed Light",
                icon = "mdi:ceiling-light",
                areaName = "Bedroom",
                deviceName = "Device #1",
            ),
            EntityPickerItem(
                entityId = "sensor.temperature",
                friendlyName = "Temperature",
                areaName = "Living Room",
            ),
            EntityPickerItem(
                entityId = "switch.fan",
                friendlyName = "Fan",
                icon = "mdi:fan",
                areaName = "Bedroom",
                deviceName = "Device #2",
            ),
        )
        var selectedEntity by remember { mutableStateOf<EntityPickerItem?>(null) }

        Box(modifier = Modifier.padding(HADimens.SPACE4)) {
            EntityPicker(
                entities = entities,
                selectedEntity = selectedEntity,
                onEntitySelected = { selectedEntity = it },
                onEntityCleared = { selectedEntity = null },
            )
        }
    }
}
