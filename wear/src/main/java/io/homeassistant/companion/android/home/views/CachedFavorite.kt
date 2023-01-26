package io.homeassistant.companion.android.home.views

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Text
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.database.wear.FavoriteCaches
import io.homeassistant.companion.android.theme.wearColorPalette
import io.homeassistant.companion.android.util.getIcon
import io.homeassistant.companion.android.util.onEntityClickedFeedback

@Composable
fun CachedFavorite(
    cached: FavoriteCaches?,
    favoriteEntityID: String,
    context: Context,
    onEntityClicked: (String, String) -> Unit,
    isHapticEnabled: Boolean,
    isToastEnabled: Boolean,
) {
    val haptic = LocalHapticFeedback.current
    Chip(
        modifier = Modifier
            .fillMaxWidth(),
        icon = {
            Image(
                asset = getIcon(cached?.icon, favoriteEntityID.split(".")[0], context) ?: CommunityMaterial.Icon.cmd_bookmark,
                colorFilter = ColorFilter.tint(wearColorPalette.onSurface)
            )
        },
        label = {
            Text(
                text = cached?.friendlyName ?: favoriteEntityID,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        onClick = {
            onEntityClicked(favoriteEntityID, "unknown")
            onEntityClickedFeedback(isToastEnabled, isHapticEnabled, context, favoriteEntityID, haptic)
        },
        colors = ChipDefaults.secondaryChipColors()
    )
}
