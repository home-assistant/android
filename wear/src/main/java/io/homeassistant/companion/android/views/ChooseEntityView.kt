package io.homeassistant.companion.android.views

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Text
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.getIcon
import io.homeassistant.companion.android.common.util.capitalize
import io.homeassistant.companion.android.data.OrderedMap
import io.homeassistant.companion.android.data.SimplifiedEntity
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.util.stringForDomain
import java.util.Locale
import io.homeassistant.companion.android.common.R as commonR

@Composable
fun ChooseEntityView(
    entitiesByDomain: OrderedMap<String, List<Entity<*>>>,
    favoriteEntityIds: State<List<String>>,
    onNoneClicked: () -> Unit,
    onEntitySelected: (entity: SimplifiedEntity) -> Unit,
    allowNone: Boolean = true
) {
    // Remember expanded state of each header
    val expandedStates = rememberExpandedStates(entitiesByDomain.orderedKeys)
    var expandedFavorites: Boolean by rememberSaveable { mutableStateOf(false) }

    WearAppTheme {
        ThemeLazyColumn {
            item {
                ListHeader(id = commonR.string.choose_entity)
            }
            if (allowNone) {
                item("key-none") {
                    Chip(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        icon = { Image(asset = CommunityMaterial.Icon.cmd_delete) },
                        label = { Text(stringResource(id = commonR.string.none)) },
                        onClick = onNoneClicked,
                        colors = ChipDefaults.primaryChipColors(
                            contentColor = Color.Black
                        )
                    )
                }
            }

            if (favoriteEntityIds.value.isNotEmpty()) {
                item("key-favorites") {
                    ExpandableListHeader(
                        string = stringResource(commonR.string.favorites),
                        expanded = expandedFavorites,
                        onExpandChanged = { expandedFavorites = it }
                    )
                }
                if (expandedFavorites) {
                    items(favoriteEntityIds.value, key = { id -> "favorite-${id}" }) { id ->
                        val favoriteEntityID = id.split(",")[0]
                        entitiesByDomain.values.flatten()
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

            for (domain in entitiesByDomain.orderedKeys) {
                val entities = entitiesByDomain[domain]
                if (!entities.isNullOrEmpty()) {
                    item(domain) {
                        ExpandableListHeader(
                            string = stringForDomain(domain, LocalContext.current)
                                ?: domain.replace('_', ' ').capitalize(Locale.getDefault()),
                            key = domain,
                            expandedStates = expandedStates
                        )
                    }
                    if (expandedStates[domain] == true) {
                        items(entities, key = { "${domain}-${it.entityId}" }) { entity ->
                            ChooseEntityChip(
                                entity = entity,
                                onEntitySelected = onEntitySelected
                            )
                        }
                    }
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
    Chip(
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
        colors = ChipDefaults.secondaryChipColors()
    )
}
