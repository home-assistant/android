package io.homeassistant.companion.android.util.compose.entity

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberStandardBottomSheetState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.ButtonSize
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.composable.HAHorizontalDivider
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
import io.homeassistant.companion.android.common.data.integration.getIcon
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse
import io.homeassistant.companion.android.util.RegistriesDataHandler
import io.homeassistant.companion.android.util.compose.safeScreenHeight
import io.homeassistant.companion.android.util.compose.screenWidth
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Breakpoint for compact screens (phones). Screens wider than this are considered tablets. */
private val COMPACT_WIDTH_BREAKPOINT = 600.dp

/** Test tag for the entity list LazyColumn, used for scrolling in tests. */
@VisibleForTesting
internal const val ENTITY_LIST_TEST_TAG = "entity_picker_list"

/**
 * Data class representing an entity in the picker with searchable metadata.
 *
 * This class contains all the information needed to display and search for an entity,
 * including optional area and device names from Home Assistant registries.
 */
internal data class EntityPickerItem(
    val entityId: String,
    val domain: String,
    val friendlyName: String,
    val icon: IIcon,
    val areaName: String? = null,
    val deviceName: String? = null,
) {
    /**
     * Returns a formatted subtitle string combining area and device name.
     *
     * The format adapts to layout direction:
     * - LTR: "Area name ▸ Device name"
     * - RTL: "Area name ◂ Device name"
     *
     * @return Formatted subtitle string, or null if both area and device are null
     */
    fun subtitle(layoutDirection: LayoutDirection): String? {
        return listOfNotNull(areaName, deviceName)
            .takeIf { it.isNotEmpty() }
            ?.joinToString(if (layoutDirection == LayoutDirection.Ltr) " ▸ " else " ◂ ")
    }

    companion object {
        /**
         * Converts an Entity to EntityPickerItem with area and device information from registries.
         */
        fun from(
            context: Context,
            entity: Entity,
            entityRegistry: List<EntityRegistryResponse>?,
            deviceRegistry: List<DeviceRegistryResponse>?,
            areaRegistry: List<AreaRegistryResponse>?,
        ): EntityPickerItem {
            val area = RegistriesDataHandler.getAreaForEntity(
                entity.entityId,
                areaRegistry,
                deviceRegistry,
                entityRegistry,
            )

            val entityReg = entityRegistry?.firstOrNull { it.entityId == entity.entityId }
            val device = entityReg?.deviceId?.let { deviceId ->
                deviceRegistry?.firstOrNull { it.id == deviceId }
            }

            return EntityPickerItem(
                entityId = entity.entityId,
                domain = entity.domain,
                friendlyName = entity.friendlyName,
                icon = entity.getIcon(context),
                areaName = area?.name,
                deviceName = device?.nameByUser ?: device?.name,
            )
        }
    }
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
 * The picker supports fuzzy search with weighted field scoring and displays entity metadata
 * (area and device names) from the registries if provided.
 *
 * @param entities The list of available entities to choose from
 * @param selectedEntityId The currently selected entity id, or null if none selected
 * @param onEntitySelectedId Callback invoked when an entity is selected
 * @param onEntityCleared Callback invoked when the selection is cleared
 * @param modifier The modifier to apply to this composable
 * @param addButtonText The text to display on the "Add entity" button when no entity is selected
 * @param entityRegistry Optional list of entity registry entries for displaying metadata (area, device)
 * @param deviceRegistry Optional list of device registry entries for displaying device names
 * @param areaRegistry Optional list of area registry entries for displaying area names
 */
@Composable
fun EntityPicker(
    entities: List<Entity>,
    selectedEntityId: String?,
    onEntitySelectedId: (String) -> Unit,
    onEntityCleared: () -> Unit,
    modifier: Modifier = Modifier,
    addButtonText: String = defaultAddText(),
    entityRegistry: List<EntityRegistryResponse>? = null,
    deviceRegistry: List<DeviceRegistryResponse>? = null,
    areaRegistry: List<AreaRegistryResponse>? = null,
) {
    val context = LocalContext.current

    // Convert Entity to EntityPickerItem on background thread to avoid ANR
    var entityPickerItems by remember { mutableStateOf<List<EntityPickerItem>>(emptyList()) }

    LaunchedEffect(entities, entityRegistry, deviceRegistry, areaRegistry) {
        entityPickerItems = withContext(Dispatchers.Default) {
            entities.map { EntityPickerItem.from(context, it, entityRegistry, deviceRegistry, areaRegistry) }
        }
    }

    EntityPicker(
        entities = entityPickerItems,
        selectedEntityId = selectedEntityId,
        onEntitySelectedId = { entityId ->
            onEntitySelectedId(entityId)
        },
        onEntityCleared = onEntityCleared,
        modifier = modifier,
        addButtonText = addButtonText,
    )
}

/**
 * Internal implementation of EntityPicker that works with EntityPickerItem.
 *
 * @param entities The list of EntityPickerItem instances with pre-computed metadata
 * @param selectedEntityId The currently selected entity id, or null if none selected
 * @param onEntitySelectedId Callback invoked when an entity is selected
 * @param onEntityCleared Callback invoked when the selection is cleared
 * @param modifier The modifier to apply to this composable
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@VisibleForTesting
internal fun EntityPicker(
    entities: List<EntityPickerItem>,
    selectedEntityId: String?,
    onEntitySelectedId: (String) -> Unit,
    onEntityCleared: () -> Unit,
    modifier: Modifier = Modifier,
    addButtonText: String = defaultAddText(),
    isExpanded: Boolean = false,
    dispatcher: CoroutineContext = Dispatchers.Default,
) {
    val screenWidth = screenWidth()
    val isCompactScreen = screenWidth < COMPACT_WIDTH_BREAKPOINT

    var isExpanded by remember { mutableStateOf(isExpanded) }
    var searchQuery by remember { mutableStateOf("") }

    val bottomSheetState = rememberStandardBottomSheetState(skipHiddenState = false)
    val scope = rememberCoroutineScope()

    Column(modifier = modifier) {
        val selectedEntity = selectedEntityId?.takeIf { it.isNotBlank() }
            ?.let { id -> entities.firstOrNull { it.entityId == id } }
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
                text = addButtonText,
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
            if (isExpanded) {
                EntityPickerBottomSheet(
                    entities = entities,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    onEntitySelected = { entity ->
                        scope.launch {
                            bottomSheetState.hide()
                            onEntitySelectedId(entity.entityId)
                            isExpanded = false
                            searchQuery = ""
                        }
                    },
                    onDismissRequest = {
                        isExpanded = false
                        searchQuery = ""
                    },
                    bottomSheetState = bottomSheetState,
                    dispatcher = dispatcher,
                )
            }
        } else {
            // Inline dropdown for tablets
            AnimatedVisibility(visible = isExpanded) {
                EntityPickerDropdown(
                    entities = entities,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    onEntitySelected = { entity ->
                        onEntitySelectedId(entity.entityId)
                        isExpanded = false
                        searchQuery = ""
                    },
                    dispatcher = dispatcher,
                    modifier = Modifier.padding(top = HADimens.SPACE2).takeIf { selectedEntityId != null } ?: Modifier,
                )
            }
        }
    }
}

@Composable
private fun defaultAddText(): String = stringResource(commonR.string.entity_picker_add_entity)

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
        asset = entity.icon,
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
        entity.subtitle(LocalLayoutDirection.current)?.let { subtitle ->
            Text(
                text = subtitle,
                style = HATextStyle.BodyMedium,
                color = colorScheme.colorTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = HADimens.SPACE1),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntityPickerBottomSheet(
    entities: List<EntityPickerItem>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onEntitySelected: (EntityPickerItem) -> Unit,
    onDismissRequest: () -> Unit,
    bottomSheetState: SheetState,
    dispatcher: CoroutineContext,
) {
    val screenHeight = safeScreenHeight() - HADimens.SPACE16

    // Consume fling velocity at content boundaries to prevent BottomSheet bounce
    val consumeFlingNestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                available

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
        }
    }

    HAModalBottomSheet(
        bottomSheetState = bottomSheetState,
        onDismissRequest = onDismissRequest,
    ) {
        EntityPickerContent(
            entities = entities,
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            onEntitySelected = onEntitySelected,
            modifier = Modifier
                .height(screenHeight)
                .nestedScroll(consumeFlingNestedScrollConnection)
                .pointerInput(Unit) {
                    // Consume vertical drag gestures to prevent BottomSheet from interpreting them as collapse gestures
                    detectVerticalDragGestures { _, _ -> }
                },
            dispatcher = dispatcher,
        )
    }
}

@Composable
private fun EntityPickerDropdown(
    entities: List<EntityPickerItem>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onEntitySelected: (EntityPickerItem) -> Unit,
    dispatcher: CoroutineContext,
    modifier: Modifier = Modifier,
) {
    val colorScheme = LocalHAColorScheme.current

    Column(
        modifier = modifier
            .enclosureBorder(colorScheme)
            .padding(top = HADimens.SPACE3),
    ) {
        EntityPickerContent(
            entities = entities,
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            onEntitySelected = onEntitySelected,
            modifier = Modifier.heightIn(max = 400.dp),
            dispatcher = dispatcher,
        )
    }
}

@Composable
private fun EntityPickerContent(
    entities: List<EntityPickerItem>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onEntitySelected: (EntityPickerItem) -> Unit,
    dispatcher: CoroutineContext,
    modifier: Modifier = Modifier,
) {
    // TODO if we make a multi entity picker we should share part of the remember that prepare the entitiesWithFields
    //  https://github.com/home-assistant/android/issues/6260
    val filteredEntities = rememberFilteredEntities(
        entities = entities,
        searchQuery = searchQuery,
        dispatcher = dispatcher,
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(HADimens.SPACE3)) {
        SearchField(searchQuery, onSearchQueryChange)

        if (filteredEntities.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ENTITY_LIST_TEST_TAG),
            ) {
                items(
                    items = filteredEntities,
                    key = { it.entityId },
                ) { entity ->
                    EntityListItem(
                        entity = entity,
                        onClick = { onEntitySelected(entity) },
                        modifier = Modifier.padding(horizontal = HADimens.SPACE3),
                    )
                    HAHorizontalDivider(modifier = Modifier.padding(start = HADimens.SPACE12))
                }
            }
        } else {
            EmptyResultPlaceholder(searchQuery)
        }
    }
}

@Composable
private fun SearchField(searchQuery: String, onSearchQueryChange: (String) -> Unit) {
    val colorScheme = LocalHAColorScheme.current
    var searchQueryRaw by remember { mutableStateOf(searchQuery) }

    // Sync local state when parent state changes (e.g., when cleared externally)
    LaunchedEffect(searchQuery) {
        if (searchQuery != searchQueryRaw) {
            searchQueryRaw = searchQuery
        }
    }

    // Debounced update to parent
    LaunchedEffect(searchQueryRaw) {
        // Skip debounce for empty strings to provide instant clear feedback
        if (searchQueryRaw.isEmpty()) {
            onSearchQueryChange(searchQueryRaw)
        } else {
            delay(300.milliseconds)
            onSearchQueryChange(searchQueryRaw)
        }
    }

    HATextField(
        value = searchQueryRaw,
        onValueChange = { searchQueryRaw = it },
        label = { Text(stringResource(commonR.string.search)) },
        trailingIcon = {
            if (searchQueryRaw.isNotEmpty()) {
                IconButton(onClick = { searchQueryRaw = "" }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(commonR.string.clear_search),
                        tint = colorScheme.colorOnNeutralNormal,
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HADimens.SPACE3),
    )
}

@Composable
private fun EmptyResultPlaceholder(searchQuery: String) {
    Row(
        modifier = Modifier.padding(start = HADimens.SPACE3, bottom = HADimens.SPACE3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = LocalHAColorScheme.current.colorOnNeutralNormal,
        )
        Text(
            text = if (searchQuery.isBlank()) {
                stringResource(commonR.string.entity_picker_no_entity_found)
            } else {
                stringResource(
                    commonR.string.entity_picker_no_entity_found_for,
                    searchQuery,
                )
            },
            style = HATextStyle.Body.copy(textAlign = TextAlign.Start),
            modifier = Modifier.padding(start = HADimens.SPACE2),
        )
    }
}

@Composable
private fun EntityListItem(entity: EntityPickerItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(HADimens.SPACE16)
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
        EntityPicker(
            entities = emptyList<EntityPickerItem>(),
            selectedEntityId = null,
            onEntitySelectedId = {},
            onEntityCleared = {},
        )
    }
}

@Preview
@Composable
private fun EntityPickerSelectedPreview() {
    HAThemeForPreview {
        val entities = listOf(
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
        var selectedEntityId by remember { mutableStateOf<String?>("light.bed") }

        EntityPicker(
            entities = entities,
            selectedEntityId = selectedEntityId,
            onEntitySelectedId = { selectedEntityId = it },
            onEntityCleared = { selectedEntityId = null },
        )
    }
}

@Preview
@Composable
private fun EntityPickerExpandedPreview() {
    HAThemeForPreview {
        val entities = listOf(
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
        var selectedEntityId by remember { mutableStateOf<String?>("light.bed") }

        EntityPicker(
            entities = entities,
            selectedEntityId = selectedEntityId,
            onEntitySelectedId = { selectedEntityId = it },
            onEntityCleared = { selectedEntityId = null },
            isExpanded = true,
        )
    }
}
