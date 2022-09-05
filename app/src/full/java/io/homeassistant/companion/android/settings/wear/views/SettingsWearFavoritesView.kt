package io.homeassistant.companion.android.settings.wear.views

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Checkbox
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Divider
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.data.integration.domain
import io.homeassistant.companion.android.settings.wear.SettingsWearViewModel
import io.homeassistant.companion.android.util.compose.getEntityDomainString
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.ReorderableLazyListState
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable
import io.homeassistant.companion.android.common.R as commonR

@Composable
fun LoadWearFavoritesSettings(
    settingsWearViewModel: SettingsWearViewModel,
    onBackClicked: () -> Unit
) {
    val reorderState = rememberReorderableLazyListState(
        onMove = { from, to -> settingsWearViewModel.onMove(from, to) },
        canDragOver = { settingsWearViewModel.canDragOver(it) },
        onDragEnd = { _, _ ->
            settingsWearViewModel.sendHomeFavorites(settingsWearViewModel.favoriteEntityIds.toList())
        }
    )

    val validEntities = settingsWearViewModel.entities.filter { it.key.split(".")[0] in settingsWearViewModel.supportedDomains }.values.sortedBy { it.entityId }.toList()
    val favoriteEntities = settingsWearViewModel.favoriteEntityIds
    Scaffold(
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
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)
                )
            }
            items(favoriteEntities.size, { favoriteEntities[it] }) { index ->
                val favoriteEntityID = favoriteEntities[index].replace("[", "").replace("]", "")
                for (entity in validEntities) {
                    if (entity.entityId == favoriteEntityID) {
                        val favoriteAttributes = entity.attributes as Map<*, *>
                        ReorderableItem(
                            reorderableState = reorderState,
                            key = favoriteEntities[index]
                        ) { isDragging ->
                            WearFavoriteEntityRow(
                                entityName = favoriteAttributes["friendly_name"].toString(),
                                entityDomain = favoriteEntityID.split('.')[0],
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
            item {
                Divider()
            }
            if (validEntities.isNotEmpty()) {
                items(validEntities.size, key = { "unchecked.${validEntities[it].entityId}" }) { index ->
                    val item = validEntities[index]
                    val itemAttributes = item.attributes as Map<*, *>
                    if (!favoriteEntities.contains(item.entityId)) {
                        WearFavoriteEntityRow(
                            entityName = itemAttributes["friendly_name"].toString(),
                            entityDomain = item.domain,
                            onClick = { settingsWearViewModel.onEntitySelected(true, item.entityId) },
                            checked = false
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
    entityDomain: String,
    onClick: () -> Unit,
    checked: Boolean,
    draggable: Boolean = false,
    isDragging: Boolean = false,
    reorderableState: ReorderableLazyListState? = null
) {
    val surfaceElevation = animateDpAsState(targetValue = if (isDragging) 8.dp else 0.dp)
    var rowModifier = Modifier
        .clickable { onClick() }
        .fillMaxWidth()
        .padding(all = 16.dp)
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
            Checkbox(
                checked = checked,
                modifier = Modifier.padding(end = 16.dp),
                onCheckedChange = null // Handled by parent Row clickable modifier
            )
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(text = entityName, style = MaterialTheme.typography.body1)
                CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                    Text(text = getEntityDomainString(entityDomain), style = MaterialTheme.typography.body2)
                }
            }
            if (draggable) {
                CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                    Image(
                        asset = CommunityMaterial.Icon.cmd_drag_horizontal_variant,
                        contentDescription = stringResource(commonR.string.hold_to_reorder),
                        colorFilter = ColorFilter.tint(LocalContentColor.current),
                        modifier = Modifier
                            .size(width = 40.dp, height = 24.dp)
                            .padding(start = 16.dp)
                            .alpha(LocalContentAlpha.current)
                    )
                }
            }
        }
    }
}
