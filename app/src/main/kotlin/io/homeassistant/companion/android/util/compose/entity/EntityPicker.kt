package io.homeassistant.companion.android.util.compose.entity

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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.ButtonSize
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.composable.HAHorizontalDivider
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.composable.HAModalBottomSheet
import io.homeassistant.companion.android.common.compose.composable.HASearchField
import io.homeassistant.companion.android.common.compose.composable.SearchFieldState
import io.homeassistant.companion.android.common.compose.composable.consumeSheetScrollFling
import io.homeassistant.companion.android.common.compose.composable.rememberHAModalBottomSheetState
import io.homeassistant.companion.android.common.compose.theme.HABorderWidth
import io.homeassistant.companion.android.common.compose.theme.HAColorScheme
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.compose.theme.MaxButtonWidth
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayItem
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.util.compose.safeScreenHeight
import io.homeassistant.companion.android.util.compose.screenWidth
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Breakpoint for compact screens (phones). Screens wider than this are considered tablets. */
private val COMPACT_WIDTH_BREAKPOINT = 600.dp

/** Test tag for the entity list LazyColumn, used for scrolling in tests. */
@VisibleForTesting
internal const val ENTITY_LIST_TEST_TAG = "entity_picker_list"

/** Test tag for the loading indicator shown while the entities are being retrieved. */
@VisibleForTesting
internal const val ENTITY_PICKER_LOADING_TEST_TAG = "entity_picker_loading"

/** Stroke width of the small loading indicator in the selected entity chip. */
private val LOADING_INDICATOR_STROKE_WIDTH = 2.dp

/** Fraction of the bottom sheet height the placeholders fill to center in the visible top half. */
private const val PLACEHOLDER_HEIGHT_FRACTION = 0.5f

/**
 * A picker component for selecting Home Assistant entities.
 *
 * This composable displays different states:
 * - When no entity is selected: Shows an "Add entity" button
 * - When expanded on small screens: Shows a bottom sheet with search and entity list
 * - When expanded on tablets: Shows an inline dropdown with search and entity list
 * - When an entity is selected: Shows a chip with the entity name and a close button
 *
 * The picker supports fuzzy search with weighted field scoring and displays the entity
 * metadata (area and device names) resolved by
 * [io.homeassistant.companion.android.common.data.integration.display.GetEntitiesForDisplayUseCase].
 * Callers without websocket access can build the display state with [rememberEntityDisplayState].
 *
 * While the display state is [EntityDisplayState.Loading] the picker shows a loading indicator in
 * place of the entity list, and the selected entity chip falls back to the raw entity id.
 *
 * @param displayState The loading state or resolved display items of the entities to choose from
 * @param selectedEntityId The currently selected entity id, or null if none selected
 * @param onSelectionChanged Callback invoked when an entity is selected, or with null when the
 * selection is cleared
 * @param modifier The modifier to apply to this composable
 * @param addButtonText The text to display on the "Add entity" button when no entity is selected
 * @param state The UI state holder controlling expansion, see [rememberEntityPickerState]
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntityPicker(
    displayState: EntityDisplayState,
    selectedEntityId: String?,
    onSelectionChanged: (String?) -> Unit,
    modifier: Modifier = Modifier,
    addButtonText: String = defaultAddText(),
    state: EntityPickerState = rememberEntityPickerState(),
) {
    val screenWidth = screenWidth()
    val isCompactScreen = screenWidth < COMPACT_WIDTH_BREAKPOINT

    val bottomSheetState = rememberHAModalBottomSheetState()
    val scope = rememberCoroutineScope()

    Column(modifier = modifier) {
        val selectedId = selectedEntityId?.takeIf { it.isNotBlank() }
        val selectedEntity = selectedId?.let { id -> (displayState as? EntityDisplayState.Loaded)?.entity(id) }
        // Show the chip while a selection is set and it resolves or is still resolving; a
        // selection absent from a loaded list is stale, so fall back to the add button.
        if (selectedId != null && (selectedEntity != null || displayState !is EntityDisplayState.Loaded)) {
            SelectedEntityChip(
                entity = selectedEntity,
                entityId = selectedId,
                isError = displayState is EntityDisplayState.Error,
                onClearClick = {
                    onSelectionChanged(null)
                    state.search.clear()
                },
                onExpandClick = { if (state.isExpanded) state.collapse() else state.expand() },
            )
        } else {
            HAFilledButton(
                text = addButtonText,
                onClick = { state.expand() },
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
            if (state.isExpanded) {
                EntityPickerBottomSheet(
                    displayState = displayState,
                    searchState = state.search,
                    onEntitySelected = { entity ->
                        scope.launch {
                            bottomSheetState.hide()
                            onSelectionChanged(entity.entityId)
                            state.collapse()
                        }
                    },
                    onDismissRequest = { state.collapse() },
                    bottomSheetState = bottomSheetState,
                    dispatcher = state.dispatcher,
                )
            }
        } else {
            // Inline dropdown for tablets
            AnimatedVisibility(visible = state.isExpanded) {
                EntityPickerDropdown(
                    displayState = displayState,
                    searchState = state.search,
                    onEntitySelected = { entity ->
                        onSelectionChanged(entity.entityId)
                        state.collapse()
                    },
                    dispatcher = state.dispatcher,
                    modifier = Modifier.padding(top = HADimens.SPACE2).takeIf { selectedEntityId != null } ?: Modifier,
                )
            }
        }
    }
}

/**
 * Builds an [EntityDisplayState] with metadata-free [EntityDisplayItem]s (no area/floor/device
 * names) from raw entities on a background thread, starting as [EntityDisplayState.Loading]
 * until the conversion completes.
 *
 * Only meant for callers that cannot reach the websocket API; everyone else should resolve
 * items with
 * [io.homeassistant.companion.android.common.data.integration.display.GetEntitiesForDisplayUseCase].
 */
@Composable
fun rememberEntityDisplayState(entities: List<Entity>): EntityDisplayState {
    val context = LocalContext.current
    var displayState by remember { mutableStateOf<EntityDisplayState>(EntityDisplayState.Loading) }
    // Conversion runs on a background dispatcher to avoid ANRs on large entity lists
    LaunchedEffect(entities) {
        displayState = withContext(Dispatchers.Default) {
            EntityDisplayState.Loaded(entities.map { EntityDisplayItem.from(entity = it, context = context) })
        }
    }
    return displayState
}

@Composable
private fun defaultAddText(): String = stringResource(commonR.string.entity_picker_add_entity)

/**
 * Chip of the currently selected entity. Renders the resolved [entity] when available,
 * otherwise a status placeholder with the raw [entityId] while the entity is still being
 * resolved ([isError] is false) or could not be loaded ([isError] is true).
 */
@Composable
private fun SelectedEntityChip(
    entity: EntityDisplayItem?,
    entityId: String,
    isError: Boolean,
    onClearClick: () -> Unit,
    onExpandClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SelectedEntityChipContainer(
        onClearClick = onClearClick,
        onExpandClick = onExpandClick,
        modifier = modifier,
    ) {
        if (entity != null) {
            EntityContent(entity)
        } else {
            UnresolvedEntityContent(entityId = entityId, isError = isError)
        }
    }
}

@Composable
private fun RowScope.UnresolvedEntityContent(entityId: String, isError: Boolean) {
    val colorScheme = LocalHAColorScheme.current
    if (isError) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = stringResource(commonR.string.entity_picker_loading_failed),
            tint = colorScheme.colorOnNeutralNormal,
            modifier = Modifier.size(HADimens.SPACE6),
        )
    } else {
        HALoading(
            modifier = Modifier.size(HADimens.SPACE6),
            strokeWidth = LOADING_INDICATOR_STROKE_WIDTH,
        )
    }
    Text(
        text = entityId,
        style = HATextStyle.Body,
        color = colorScheme.colorTextPrimary,
        maxLines = 1,
        textAlign = TextAlign.Start,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
    )
}

@Composable
private fun SelectedEntityChipContainer(
    onClearClick: () -> Unit,
    onExpandClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
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
        content()
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
private fun RowScope.EntityContent(entity: EntityDisplayItem) {
    val colorScheme = LocalHAColorScheme.current
    Image(
        asset = entity.icon,
        colorFilter = ColorFilter.tint(colorScheme.colorTextSecondary),
        contentDescription = null,
        modifier = Modifier.size(HADimens.SPACE6),
    )
    Column(modifier = Modifier.weight(1f)) {
        Text(
            text = entity.name,
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
    displayState: EntityDisplayState,
    searchState: SearchFieldState,
    onEntitySelected: (EntityDisplayItem) -> Unit,
    onDismissRequest: () -> Unit,
    bottomSheetState: SheetState,
    dispatcher: CoroutineContext,
) {
    val screenHeight = safeScreenHeight() - HADimens.SPACE16

    HAModalBottomSheet(
        bottomSheetState = bottomSheetState,
        onDismissRequest = onDismissRequest,
    ) {
        EntityPickerContent(
            displayState = displayState,
            searchState = searchState,
            onEntitySelected = onEntitySelected,
            modifier = Modifier
                .height(screenHeight)
                .consumeSheetScrollFling(),
            dispatcher = dispatcher,
            // The sheet content is a fixed full-screen height while the sheet opens partially
            // expanded, so filling half the height centers the placeholder in the visible top
            // half without expanding the sheet.
            placeholderModifier = Modifier.fillMaxHeight(fraction = PLACEHOLDER_HEIGHT_FRACTION),
        )
    }
}

@Composable
private fun EntityPickerDropdown(
    displayState: EntityDisplayState,
    searchState: SearchFieldState,
    onEntitySelected: (EntityDisplayItem) -> Unit,
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
            displayState = displayState,
            searchState = searchState,
            onEntitySelected = onEntitySelected,
            modifier = Modifier.heightIn(max = 400.dp),
            dispatcher = dispatcher,
        )
    }
}

@Composable
private fun EntityPickerContent(
    displayState: EntityDisplayState,
    searchState: SearchFieldState,
    onEntitySelected: (EntityDisplayItem) -> Unit,
    dispatcher: CoroutineContext,
    modifier: Modifier = Modifier,
    // Height behaviour of the loading/error/empty placeholders. The bottom sheet has a fixed
    // height and passes a fraction fill to center in its visible half; the dropdown wraps its
    // content and leaves it at the default, so the placeholder is naturally sized.
    placeholderModifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(HADimens.SPACE3)) {
        HASearchField(
            state = searchState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HADimens.SPACE3),
        )

        when (displayState) {
            is EntityDisplayState.Loading -> LoadingPlaceholder(placeholderModifier)
            is EntityDisplayState.Error -> ErrorPlaceholder(placeholderModifier)
            is EntityDisplayState.Loaded -> LoadedEntityList(
                entities = displayState.entities,
                searchQuery = searchState.query,
                onEntitySelected = onEntitySelected,
                dispatcher = dispatcher,
                placeholderModifier = placeholderModifier,
            )
        }
    }
}

@Composable
private fun PlaceholderContainer(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier)
            .padding(vertical = HADimens.SPACE6),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun LoadingPlaceholder(modifier: Modifier = Modifier) {
    PlaceholderContainer(modifier = modifier.testTag(ENTITY_PICKER_LOADING_TEST_TAG)) {
        HALoading()
    }
}

@Composable
private fun LoadedEntityList(
    entities: Collection<EntityDisplayItem>,
    searchQuery: String,
    onEntitySelected: (EntityDisplayItem) -> Unit,
    dispatcher: CoroutineContext,
    modifier: Modifier = Modifier,
    placeholderModifier: Modifier = Modifier,
) {
    // TODO if we make a multi entity picker we should share part of the remember that prepare the entitiesWithFields
    //  https://github.com/home-assistant/android/issues/6260
    val filteredEntities = rememberFilteredEntities(
        entities = entities,
        searchQuery = searchQuery,
        dispatcher = dispatcher,
    )

    when {
        filteredEntities == null -> LoadingPlaceholder(placeholderModifier)
        filteredEntities.isEmpty() -> EmptyResultPlaceholder(searchQuery, placeholderModifier)
        else -> LazyColumn(
            modifier = modifier
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
    }
}

@Composable
private fun ErrorPlaceholder(modifier: Modifier = Modifier) {
    PlaceholderContainer(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = LocalHAColorScheme.current.colorOnNeutralNormal,
            )
            Text(
                text = stringResource(commonR.string.entity_picker_loading_failed),
                style = HATextStyle.Body.copy(textAlign = TextAlign.Start),
                modifier = Modifier.padding(start = HADimens.SPACE2),
            )
        }
    }
}

@Composable
private fun EmptyResultPlaceholder(searchQuery: String, modifier: Modifier = Modifier) {
    PlaceholderContainer(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
}

@Composable
private fun EntityListItem(entity: EntityDisplayItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
            displayState = EntityDisplayState.Loaded(emptyList()),
            selectedEntityId = null,
            onSelectionChanged = {},
        )
    }
}

private fun previewEntities() = listOf(
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
        icon = CommunityMaterial.Icon3.cmd_temperature_celsius,
        areaName = "Living Room",
    ),
    EntityDisplayItem(
        entityId = "switch.fan",
        name = "Fan",
        icon = CommunityMaterial.Icon2.cmd_fan,
        areaName = "Bedroom",
        deviceName = "Device #2",
    ),
)

@Preview
@Composable
private fun EntityPickerSelectedPreview() {
    HAThemeForPreview {
        var selectedEntityId by remember { mutableStateOf<String?>("light.bed") }

        EntityPicker(
            displayState = EntityDisplayState.Loaded(previewEntities()),
            selectedEntityId = selectedEntityId,
            onSelectionChanged = { selectedEntityId = it },
        )
    }
}

@Preview
@Composable
private fun EntityPickerExpandedPreview() {
    HAThemeForPreview {
        var selectedEntityId by remember { mutableStateOf<String?>("light.bed") }

        EntityPicker(
            displayState = EntityDisplayState.Loaded(previewEntities()),
            selectedEntityId = selectedEntityId,
            onSelectionChanged = { selectedEntityId = it },
            state = rememberEntityPickerState(isExpanded = true),
        )
    }
}

@Preview
@Composable
private fun EntityPickerLoadingPreview() {
    HAThemeForPreview {
        Column {
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
                modifier = Modifier.padding(top = HADimens.SPACE4),
            )
        }
    }
}
