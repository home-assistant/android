package io.homeassistant.companion.android.home.views

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.home.HomePresenterImpl
import io.homeassistant.companion.android.theme.wearColorPalette
import io.homeassistant.companion.android.util.getIcon
import io.homeassistant.companion.android.util.onEntityClickedFeedback
import io.homeassistant.companion.android.util.previewEntity1
import io.homeassistant.companion.android.util.previewEntity3

@Composable
fun EntityUi(
    entity: Entity<*>,
    onEntityClicked: (String, String) -> Unit,
    isHapticEnabled: Boolean,
    isToastEnabled: Boolean
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val attributes = entity.attributes as Map<*, *>
    val iconBitmap = getIcon(attributes["icon"] as String?, entity.entityId.split(".")[0], LocalContext.current)
    val friendlyName = attributes["friendly_name"].toString()

    if (entity.entityId.split(".")[0] in HomePresenterImpl.toggleDomains) {
        ToggleChip(
            checked = entity.state == "on" || entity.state == "locked",
            onCheckedChange = {
                onEntityClicked(entity.entityId, entity.state)
                onEntityClickedFeedback(isToastEnabled, isHapticEnabled, context, friendlyName, haptic)
            },
            modifier = Modifier
                .fillMaxWidth(),
            appIcon = {
                Image(
                    asset = iconBitmap ?: CommunityMaterial.Icon.cmd_cellphone,
                    colorFilter = ColorFilter.tint(wearColorPalette.onSurface)
                )
            },
            label = {
                Text(
                    text = friendlyName,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            },
            enabled = entity.state != "unavailable",
            toggleIcon = { ToggleChipDefaults.SwitchIcon(entity.state == "on") }
        )
    } else {
        Chip(
            modifier = Modifier
                .fillMaxWidth(),
            icon = {
                Image(
                    asset = iconBitmap ?: CommunityMaterial.Icon.cmd_cellphone,
                    colorFilter = ColorFilter.tint(wearColorPalette.onSurface)
                )
            },
            label = {
                Text(
                    text = friendlyName,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            },
            enabled = entity.state != "unavailable",
            onClick = {
                onEntityClicked(entity.entityId, entity.state)
                onEntityClickedFeedback(isToastEnabled, isHapticEnabled, context, friendlyName, haptic)
            },
            colors = ChipDefaults.secondaryChipColors()
        )
    }
}

@Preview
@Composable
private fun PreviewEntityUI() {
    Column {
        EntityUi(
            entity = previewEntity1,
            onEntityClicked = { _, _ -> },
            isHapticEnabled = true,
            isToastEnabled = false
        )
        EntityUi(
            entity = previewEntity3,
            onEntityClicked = { _, _ -> },
            isHapticEnabled = false,
            isToastEnabled = true
        )
    }
}
