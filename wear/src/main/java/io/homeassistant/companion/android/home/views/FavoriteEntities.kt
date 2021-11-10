package io.homeassistant.companion.android.home.views

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Text
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.util.getIcon

@Composable
fun FavoriteEntities(
    favorite: String,
    entity: Entity<*>?,
    onEntityClicked: (String) -> Unit
) {
    val favoriteEntityID = favorite.split(",")[0]
    val favoriteName = favorite.split(",")[1]
    val favoriteIcon = favorite.split(",")[2]
    if (entity == null) {
        // Use a normal chip when we don't have the state of the entity
        Chip(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            icon = {
                Image(
                    asset = getIcon(
                        favoriteIcon,
                        favoriteEntityID.split(".")[0],
                        LocalContext.current
                    )
                        ?: CommunityMaterial.Icon.cmd_cellphone
                )
            },
            label = {
                Text(
                    text = favoriteName,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            },
            onClick = { onEntityClicked(favoriteEntityID) },
            colors = ChipDefaults.primaryChipColors(
                backgroundColor = colorResource(id = R.color.colorAccent),
                contentColor = Color.Black
            )
        )
    } else {
        EntityUi(
            entity = entity,
            onEntityClicked = { onEntityClicked(entity.entityId) }
        )
    }
}