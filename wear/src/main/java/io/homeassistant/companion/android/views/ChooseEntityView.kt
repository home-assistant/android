package io.homeassistant.companion.android.views

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Text
import androidx.wear.tooling.preview.devices.WearDevices
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.getIcon
import io.homeassistant.companion.android.common.util.capitalize
import io.homeassistant.companion.android.data.SimplifiedEntity
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.theme.getFilledTonalButtonColors
import io.homeassistant.companion.android.util.playPreviewEntityScene1
import io.homeassistant.companion.android.util.playPreviewEntityScene2
import io.homeassistant.companion.android.util.stringForDomain
import java.util.Locale

@Composable
fun ChooseEntityView(
    entitiesByDomainOrder: SnapshotStateList<String>,
    entitiesByDomain: SnapshotStateMap<String, SnapshotStateList<Entity<*>>>,
    favoriteEntityIds: State<List<String>>,
    onNoneClicked: () -> Unit,
    onEntitySelected: (entity: SimplifiedEntity) -> Unit,
    allowNone: Boolean = true
) {
    // Remember expanded state of each header
    val expandedStates = rememberExpandedStates(entitiesByDomainOrder)
    var expandedFavorites: Boolean by rememberSaveable { mutableStateOf(false) }
    var expandedAppShortcuts: Boolean by rememberSaveable { mutableStateOf(false) }

    WearAppTheme {
        ThemeLazyColumn {
            item {
                ListHeader(id = commonR.string.choose_entity)
            }
            if (allowNone) {
                item {
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        icon = { Image(asset = CommunityMaterial.Icon.cmd_delete) },
                        label = { Text(stringResource(id = commonR.string.none)) },
                        onClick = onNoneClicked,
                        colors = ButtonDefaults.buttonColors(
                            contentColor = Color.Black
                        )
                    )
                }
            }

            if (favoriteEntityIds.value.isNotEmpty()) {
                item {
                    ExpandableListHeader(
                        string = stringResource(commonR.string.favorites),
                        expanded = expandedFavorites,
                        onExpandChanged = { expandedFavorites = it }
                    )
                }
                if (expandedFavorites) {
                    items(favoriteEntityIds.value.size) { index ->
                        val favoriteEntityID = favoriteEntityIds.value[index].split(",")[0]
                        entitiesByDomain.flatMap { (_, values) -> values }
                            .firstOrNull { it.entityId == favoriteEntityID }
                            ?.let {
                                ChooseEntityChip(
                                    entity = it,
                                    onEntitySelected = onEntitySelected
                                )
                            }
                    }
                }
            }

            for (domain in entitiesByDomainOrder) {
                val entities = entitiesByDomain[domain]
                if (!entities.isNullOrEmpty()) {
                    item {
                        ExpandableListHeader(
                            string = stringForDomain(domain, LocalContext.current)
                                ?: domain.replace('_', ' ').capitalize(Locale.getDefault()),
                            key = domain,
                            expandedStates = expandedStates
                        )
                    }
                    if (expandedStates[domain] == true) {
                        items(entities, key = { it.entityId }) { entity ->
                            ChooseEntityChip(
                                entity = entity,
                                onEntitySelected = onEntitySelected
                            )
                        }
                    }
                }
            }

            // App Shortcuts
            item {
                ExpandableListHeader(
                    string = stringResource(commonR.string.shortcuts),
                    expanded = expandedAppShortcuts,
                    onExpandChanged = { expandedAppShortcuts = it }
                )
            }
            if (expandedAppShortcuts) {
                // HomeAssistant app shortcut
                item {
                    Button(
                        modifier = Modifier
                            .fillMaxWidth(),
                        icon = {
                            Image(
                                asset = CommunityMaterial.Icon2.cmd_home_assistant,
                                colorFilter = ColorFilter.tint(Color.White)
                            )
                        },
                        label = { Text(stringResource(id = commonR.string.app_name)) },
                        onClick = {
                            onEntitySelected(
                                SimplifiedEntity(
                                    "app_shortcut.home_assistant",
                                    "Home Assistant",
                                    "mdi:home-assistant"
                                )
                            )
                        },
                        colors = getFilledTonalButtonColors()
                    )
                }
                // Assist shortcut
                item {
                    Button(
                        modifier = Modifier
                            .fillMaxWidth(),
                        icon = {
                            Image(
                                asset = CommunityMaterial.Icon.cmd_comment_processing_outline,
                                colorFilter = ColorFilter.tint(Color.White)
                            )
                        },
                        label = { Text(stringResource(id = commonR.string.assist)) },
                        onClick = {
                            onEntitySelected(
                                SimplifiedEntity(
                                    "app_shortcut.assist",
                                    "Assist",
                                    "mdi:comment-processing-outline"
                                )
                            )
                        },
                        colors = getFilledTonalButtonColors()
                    )
                }
            }
        }
    }
}

@Composable
private fun ChooseEntityChip(
    entity: Entity<*>,
    onEntitySelected: (entity: SimplifiedEntity) -> Unit
) {
    val attributes = entity.attributes as Map<*, *>
    val iconBitmap = entity.getIcon(LocalContext.current)
    Button(
        modifier = Modifier
            .fillMaxWidth(),
        icon = {
            Image(
                asset = iconBitmap,
                colorFilter = ColorFilter.tint(Color.White)
            )
        },
        label = {
            Text(
                text = attributes["friendly_name"].toString(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        onClick = {
            onEntitySelected(
                SimplifiedEntity(
                    entity.entityId,
                    attributes["friendly_name"] as String? ?: entity.entityId,
                    attributes["icon"] as String? ?: ""
                )
            )
        },
        colors = getFilledTonalButtonColors()
    )
}

@Preview(device = WearDevices.LARGE_ROUND)
@Composable
fun ChooseEntityViewEmptyPreview() {
    ChooseEntityView(
        entitiesByDomainOrder = remember {
            mutableStateListOf()
        },
        entitiesByDomain = remember {
            mutableStateMapOf()
        },
        favoriteEntityIds = remember { mutableStateOf(listOf()) },
        onNoneClicked = {},
        onEntitySelected = {},
        allowNone = true
    )
}

@Preview(device = WearDevices.LARGE_ROUND)
@Composable
fun ChooseEntityViewWithDataPreview() {
    ChooseEntityView(
        entitiesByDomainOrder = remember {
            mutableStateListOf(playPreviewEntityScene1.entityId, playPreviewEntityScene2.entityId)
        },
        entitiesByDomain = remember {
            mutableStateMapOf(
                Pair(
                    playPreviewEntityScene1.entityId,
                    mutableStateListOf(playPreviewEntityScene1)
                ),
                Pair(
                    playPreviewEntityScene2.entityId,
                    mutableStateListOf(playPreviewEntityScene2)
                )
            )
        },
        favoriteEntityIds = remember { mutableStateOf(listOf(playPreviewEntityScene1.entityId)) },
        onNoneClicked = {},
        onEntitySelected = {},
        allowNone = false
    )
}
