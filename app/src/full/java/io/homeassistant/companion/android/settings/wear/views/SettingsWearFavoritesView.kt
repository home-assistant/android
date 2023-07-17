package io.homeassistant.companion.android.settings.wear.views

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.friendlyName
import io.homeassistant.companion.android.settings.wear.SettingsWearViewModel
import io.homeassistant.companion.android.util.compose.SingleEntityPicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.ReorderableLazyListState
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable
import io.homeassistant.companion.android.common.R as commonR

@Composable
fun LoadWearFavoritesSettings(
    settingsWearViewModel: SettingsWearViewModel,
    onBackClicked: () -> Unit,
    events: SharedFlow<String>
) {
    val reorderState = rememberReorderableLazyListState(
        onMove = { from, to -> settingsWearViewModel.onMove(from, to) },
        canDragOver = { draggedOver, _ -> settingsWearViewModel.canDragOver(draggedOver) },
        onDragEnd = { _, _ ->
            settingsWearViewModel.sendHomeFavorites(settingsWearViewModel.favoriteEntityIds.toList())
        }
    )

    val favoriteEntities = settingsWearViewModel.favoriteEntityIds
    var validEntities by remember { mutableStateOf<List<Entity<*>>>(emptyList()) }
    LaunchedEffect(favoriteEntities.size) {
        validEntities = withContext(Dispatchers.IO) {
            settingsWearViewModel.entities
                .filter {
                    !favoriteEntities.contains(it.key) &&
                        it.key.split(".")[0] in settingsWearViewModel.supportedDomains
                }
                .values
                .toList()
        }
    }

    val scaffoldState = rememberScaffoldState()
    LaunchedEffect("snackbar") {
        events.onEach { message ->
            scaffoldState.snackbarHostState.currentSnackbarData?.dismiss() // in case of rapid-fire events
            scaffoldState.snackbarHostState.showSnackbar(message)
        }.launchIn(this)
    }

    Scaffold(
        scaffoldState = scaffoldState,
        topBar = {
            SettingsWearTopAppBar(
                title = { Text(stringResource(commonR.string.wear_favorite_entities)) },
                onBackClicked = onBackClicked,
                docsLink = WEAR_DOCS_LINK
            )
        }
    ) { contentPadding ->
        LazyColumn(
            state = reorderState.listState,
            verticalArrangement = Arrangement.Center,
            contentPadding = PaddingValues(vertical = 16.dp),
            modifier = Modifier
                .padding(contentPadding)
                .reorderable(reorderState)
        ) {
            item {
                Text(
                    text = stringResource(commonR.string.wear_set_favorites),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            item {
                SingleEntityPicker(
                    entities = validEntities,
                    currentEntity = null,
                    onEntityCleared = { /* Nothing */ },
                    onEntitySelected = {
                        settingsWearViewModel.onEntitySelected(true, it)
                        return@SingleEntityPicker false // Clear input
                    },
                    modifier = Modifier.padding(all = 16.dp),
                    label = { Text(stringResource(commonR.string.add_favorite)) }
                )
            }
            items(favoriteEntities.size, { favoriteEntities[it] }) { index ->
                val favoriteEntityID = favoriteEntities[index].replace("[", "").replace("]", "")
                settingsWearViewModel.entities[favoriteEntityID]?.let {
                    ReorderableItem(
                        reorderableState = reorderState,
                        key = favoriteEntities[index]
                    ) { isDragging ->
                        WearFavoriteEntityRow(
                            entityName = it.friendlyName,
                            entityId = favoriteEntityID,
                            onClick = {
                                settingsWearViewModel.onEntitySelected(
                                    false,
                                    favoriteEntities[index]
                                )
                            },
                            checked = favoriteEntities.contains(favoriteEntities[index]),
                            draggable = true,
                            isDragging = isDragging,
                            reorderableState = reorderState
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WearFavoriteEntityRow(
    entityName: String,
    entityId: String,
    onClick: () -> Unit,
    checked: Boolean,
    draggable: Boolean = false,
    isDragging: Boolean = false,
    reorderableState: ReorderableLazyListState? = null
) {
    val surfaceElevation = animateDpAsState(targetValue = if (isDragging) 8.dp else 0.dp)
    var rowModifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
    if (draggable && reorderableState != null) {
        rowModifier = rowModifier.then(Modifier.detectReorderAfterLongPress(reorderableState))
    }
    Surface(
        elevation = surfaceElevation.value
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = rowModifier
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(start = 16.dp)
            ) {
                Text(text = entityName, style = MaterialTheme.typography.body1)
                CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                    Text(text = entityId, style = MaterialTheme.typography.body2)
                }
            }
            IconButton(onClick = onClick) {
                Icon(
                    imageVector = if (checked) Icons.Default.Clear else Icons.Default.Add,
                    contentDescription = stringResource(if (checked) commonR.string.delete else commonR.string.add_favorite)
                )
            }
            if (draggable) {
                CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                    Image(
                        asset = CommunityMaterial.Icon.cmd_drag_horizontal_variant,
                        contentDescription = stringResource(commonR.string.hold_to_reorder),
                        colorFilter = ColorFilter.tint(LocalContentColor.current),
                        modifier = Modifier
                            .size(width = 40.dp, height = 24.dp)
                            .padding(end = 16.dp)
                            .alpha(LocalContentAlpha.current)
                    )
                }
            }
        }
    }
}
