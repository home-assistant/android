package io.homeassistant.companion.android.home.views

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.ToggleButton
import com.mikepenz.iconics.compose.Image
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.getIcon
import io.homeassistant.companion.android.home.MainViewModel
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.theme.getToggleButtonColors
import io.homeassistant.companion.android.theme.wearColorScheme
import io.homeassistant.companion.android.util.ToggleSwitch
import io.homeassistant.companion.android.views.ExpandableListHeader
import io.homeassistant.companion.android.views.ListHeader
import io.homeassistant.companion.android.views.ThemeLazyColumn
import io.homeassistant.companion.android.views.rememberExpandedStates

@Composable
fun SetFavoritesView(
    mainViewModel: MainViewModel,
    favoriteEntityIds: List<String>,
    onFavoriteSelected: (entityId: String, isSelected: Boolean) -> Unit
) {
    // Remember expanded state of each header
    val expandedStates = rememberExpandedStates(mainViewModel.supportedDomains())

    WearAppTheme {
        ThemeLazyColumn {
            item {
                ListHeader(id = commonR.string.set_favorite)
            }
            for (domain in mainViewModel.entitiesByDomainOrder) {
                val entities = mainViewModel.entitiesByDomain[domain].orEmpty()
                if (entities.isNotEmpty()) {
                    item {
                        ExpandableListHeader(
                            string = mainViewModel.stringForDomain(domain)!!,
                            key = domain,
                            expandedStates = expandedStates
                        )
                    }
                    if (expandedStates[domain] == true) {
                        items(entities, key = { it.entityId }) { entity ->
                            FavoriteToggleChip(
                                entity = entity,
                                favoriteEntityIds = favoriteEntityIds,
                                onFavoriteSelected = onFavoriteSelected
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteToggleChip(
    entity: Entity<*>,
    favoriteEntityIds: List<String>,
    onFavoriteSelected: (entityId: String, isSelected: Boolean) -> Unit
) {
    val attributes = entity.attributes as Map<*, *>
    val iconBitmap = entity.getIcon(LocalContext.current)

    val entityId = entity.entityId
    val checked = favoriteEntityIds.contains(entityId)
    ToggleButton(
        checked = checked,
        onCheckedChange = {
            onFavoriteSelected(entityId, it)
        },
        modifier = Modifier
            .fillMaxWidth(),
        icon = {
            Image(
                asset = iconBitmap,
                colorFilter = ColorFilter.tint(wearColorScheme.onSurface)
            )
        },
        label = {
            Text(
                text = attributes["friendly_name"].toString(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        toggleControl = { ToggleSwitch(checked) },
        colors = getToggleButtonColors()
    )
}
