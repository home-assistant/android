package io.homeassistant.companion.android.settings.vehicle.views

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.data.integration.friendlyName
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.settings.vehicle.ManageAndroidAutoViewModel
import io.homeassistant.companion.android.util.compose.FavoriteEntityRow
import io.homeassistant.companion.android.util.compose.ServerExposedDropdownMenu
import io.homeassistant.companion.android.util.compose.SingleEntityPicker
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable
import io.homeassistant.companion.android.common.R as commonR

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AndroidAutoFavoritesSettings(
    androidAutoViewModel: ManageAndroidAutoViewModel,
    serversList: List<Server>,
    defaultServer: Int
) {
    val reorderState = rememberReorderableLazyListState(
        onMove = { from, to -> androidAutoViewModel.onMove(from, to) },
        canDragOver = { draggedOver, _ -> androidAutoViewModel.canDragOver(draggedOver) },
        onDragEnd = { _, _ -> androidAutoViewModel.saveFavorites() }
    )

    var selectedServer by remember { mutableStateOf(defaultServer) }

    val favoriteEntities = androidAutoViewModel.favoritesList

    LazyColumn(
        state = reorderState.listState,
        contentPadding = PaddingValues(vertical = 16.dp),
        modifier = Modifier
            .padding(16.dp)
            .reorderable(reorderState)
    ) {
        item {
            Text(
                text = stringResource(commonR.string.aa_set_favorites),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        if (serversList.size > 1) {
            item {
                ServerExposedDropdownMenu(
                    servers = serversList,
                    current = selectedServer,
                    onSelected = {
                        androidAutoViewModel.loadEntities(it)
                        selectedServer = it
                    },
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                )
            }
        }
        item {
            SingleEntityPicker(
                entities = androidAutoViewModel.sortedEntities,
                currentEntity = null,
                onEntityCleared = { /* Nothing */ },
                onEntitySelected = {
                    androidAutoViewModel.onEntitySelected(true, it, selectedServer)
                    return@SingleEntityPicker false // Clear input
                },
                modifier = Modifier.padding(all = 16.dp),
                label = { Text(stringResource(commonR.string.add_favorite)) }
            )
        }
        if (favoriteEntities.isNotEmpty() && androidAutoViewModel.sortedEntities.isNotEmpty()) {
            items(favoriteEntities.size, { favoriteEntities[it] }) { index ->
                val favoriteEntityID =
                    favoriteEntities[index].replace("[", "").replace("]", "").split("-")[1]
                Log.d("AAVM", "$favoriteEntityID is found")
                androidAutoViewModel.sortedEntities.filter { it.entityId == favoriteEntityID }.let {
                    ReorderableItem(
                        reorderableState = reorderState,
                        key = favoriteEntities[index]
                    ) { isDragging ->
                        FavoriteEntityRow(
                            entityName = it.first().friendlyName,
                            entityId = it.first().entityId,
                            onClick = {
                                androidAutoViewModel.onEntitySelected(
                                    false,
                                    it.first().entityId,
                                    selectedServer
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
