package io.homeassistant.companion.android.settings.wear.views

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Checkbox
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.IconicsDrawable
import io.homeassistant.companion.android.settings.wear.SettingsWearViewModel
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.draggedItem
import org.burnoutcrew.reorderable.rememberReorderState
import org.burnoutcrew.reorderable.reorderable
import io.homeassistant.companion.android.common.R as commonR

const val WEAR_DOCS_LINK = "https://companion.home-assistant.io/docs/wear-os/wear-os"
val supportedDomains = listOf(
    "input_boolean", "light", "lock", "switch", "script", "scene"
)

@Composable
fun LoadWearFavoritesSettings(
    settingsWearViewModel: SettingsWearViewModel
) {
    val context = LocalContext.current
    val reorderState = rememberReorderState()

    val validEntities = settingsWearViewModel.entities.filter { it.key.split(".")[0] in supportedDomains }.values.sortedBy { it.entityId }.toList()
    val favoriteEntities = settingsWearViewModel.favoriteEntityIds
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(commonR.string.wear_favorite_entities)) },
                actions = {
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(WEAR_DOCS_LINK))
                        context.startActivity(intent)
                    }) {
                        Icon(
                            Icons.Filled.HelpOutline,
                            contentDescription = stringResource(id = commonR.string.help)
                        )
                    }
                }
            )
        }
    ) {
        LazyColumn(
            state = reorderState.listState,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(top = 10.dp, start = 5.dp, end = 10.dp)
                .then(
                    Modifier.reorderable(
                        reorderState,
                        { from, to -> settingsWearViewModel.onMove(from, to) },
                        canDragOver = { settingsWearViewModel.canDragOver(it) },
                        onDragEnd = { _, _ ->
                            settingsWearViewModel.sendHomeFavorites(settingsWearViewModel.favoriteEntityIds.toList())
                        }
                    )
                )
        ) {
            item {
                Text(
                    text = stringResource(commonR.string.wear_set_favorites),
                    fontWeight = FontWeight.Bold
                )
            }
            items(favoriteEntities.size, { favoriteEntities[it] }) { index ->
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .clickable {
                            settingsWearViewModel.onEntitySelected(
                                false,
                                favoriteEntities[index]
                            )
                        }
                        .draggedItem(
                            reorderState.offsetByKey(favoriteEntities[index]),
                            Orientation.Vertical
                        )
                        .detectReorderAfterLongPress(reorderState)
                ) {
                    val iconBitmap = IconicsDrawable(LocalContext.current, "cmd-drag_vertical").toBitmap().asImageBitmap()
                    Icon(iconBitmap, "", modifier = Modifier.padding(top = 13.dp))
                    Checkbox(
                        checked = favoriteEntities.contains(favoriteEntities[index]),
                        onCheckedChange = {
                            settingsWearViewModel.onEntitySelected(it, favoriteEntities[index])
                        },
                        modifier = Modifier.padding(end = 5.dp)
                    )
                    Text(
                        text = favoriteEntities[index].replace("[", "").replace("]", ""),
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }
            item {
                Divider()
            }
            if (!validEntities.isNullOrEmpty()) {
                items(validEntities.size) { index ->
                    val item = validEntities[index]
                    if (!favoriteEntities.contains(item.entityId)) {
                        Row(
                            modifier = Modifier
                                .padding(15.dp)
                                .clickable {
                                    settingsWearViewModel.onEntitySelected(true, item.entityId)
                                }
                        ) {
                            Checkbox(
                                checked = false,
                                onCheckedChange = {
                                    settingsWearViewModel.onEntitySelected(it, item.entityId)
                                },
                                modifier = Modifier.padding(end = 5.dp)
                            )
                            Text(
                                text = item.entityId,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
