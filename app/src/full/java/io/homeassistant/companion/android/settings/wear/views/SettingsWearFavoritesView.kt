package io.homeassistant.companion.android.settings.wear.views

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.sp
import com.mikepenz.iconics.IconicsDrawable
import io.homeassistant.companion.android.common.data.integration.domain
import io.homeassistant.companion.android.settings.wear.SettingsWearViewModel
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.draggedItem
import org.burnoutcrew.reorderable.rememberReorderState
import org.burnoutcrew.reorderable.reorderable
import io.homeassistant.companion.android.common.R as commonR

const val WEAR_DOCS_LINK = "https://companion.home-assistant.io/docs/wear-os/"

@Composable
fun LoadWearFavoritesSettings(
    settingsWearViewModel: SettingsWearViewModel
) {
    val context = LocalContext.current
    val reorderState = rememberReorderState()

    val validEntities = settingsWearViewModel.entities.filter { it.key.split(".")[0] in settingsWearViewModel.supportedDomains }.values.sortedBy { it.entityId }.toList()
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
                val favoriteEntityID = favoriteEntities[index].replace("[", "").replace("]", "")
                for (entity in validEntities)
                    if (entity.entityId == favoriteEntityID) {
                        val favoriteAttributes = entity.attributes as Map<*, *>
                        val favoriteFriendlyName = favoriteAttributes["friendly_name"].toString()
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
                            val iconBitmap =
                                IconicsDrawable(LocalContext.current, "cmd-drag_vertical").toBitmap()
                                    .asImageBitmap()
                            Icon(iconBitmap, "", modifier = Modifier.padding(top = 13.dp))
                            Checkbox(
                                checked = favoriteEntities.contains(favoriteEntities[index]),
                                onCheckedChange = {
                                    settingsWearViewModel.onEntitySelected(it, favoriteEntities[index])
                                },
                                modifier = Modifier.padding(end = 5.dp)
                            )
                            Column {
                                Text(
                                    text = favoriteFriendlyName,
                                    modifier = Modifier.padding(top = 10.dp)
                                )
                                Text(
                                    text = getDomainString(favoriteEntityID.split('.')[0]),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
            }
            item {
                Divider()
            }
            if (!validEntities.isNullOrEmpty()) {
                items(validEntities.size) { index ->
                    val item = validEntities[index]
                    val itemAttributes = item.attributes as Map<*, *>
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
                            Column {
                                Text(
                                    text = itemAttributes["friendly_name"].toString(),
                                    modifier = Modifier.padding(top = 10.dp)
                                )
                                Text(
                                    text = getDomainString(item.domain),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun getDomainString(domain: String): String {
    return when (domain) {
        "button" -> stringResource(commonR.string.domain_button)
        "cover" -> stringResource(commonR.string.domain_cover)
        "fan" -> stringResource(commonR.string.domain_fan)
        "input_boolean" -> stringResource(commonR.string.domain_input_boolean)
        "input_button" -> stringResource(commonR.string.domain_input_button)
        "light" -> stringResource(commonR.string.domain_light)
        "lock" -> stringResource(commonR.string.domain_lock)
        "scene" -> stringResource(commonR.string.domain_scene)
        "script" -> stringResource(commonR.string.domain_script)
        "switch" -> stringResource(commonR.string.domain_switch)
        else -> ""
    }
}
