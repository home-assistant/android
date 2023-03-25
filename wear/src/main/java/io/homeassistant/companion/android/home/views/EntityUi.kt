package io.homeassistant.companion.android.home.views

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.domain
import io.homeassistant.companion.android.common.data.integration.getIcon
import io.homeassistant.companion.android.home.HomePresenterImpl
import io.homeassistant.companion.android.theme.wearColorPalette
import io.homeassistant.companion.android.util.WearToggleChip
import io.homeassistant.companion.android.util.onEntityClickedFeedback
import io.homeassistant.companion.android.util.previewEntity1
import io.homeassistant.companion.android.util.previewEntity3

@Composable
fun EntityUi(
    entity: Entity<*>,
    onEntityClicked: (String, String) -> Unit,
    isHapticEnabled: Boolean,
    isToastEnabled: Boolean,
    onEntityLongPressed: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val attributes = entity.attributes as Map<*, *>
    val iconBitmap = entity.getIcon(LocalContext.current)
    val friendlyName = attributes["friendly_name"].toString()

    if (entity.domain in HomePresenterImpl.toggleDomains) {
        val isChecked = entity.state in listOf("on", "locked", "open", "opening")
        ToggleChip(
            checked = isChecked,
            onCheckedChange = {
                onEntityClicked(entity.entityId, entity.state)
                onEntityClickedFeedback(isToastEnabled, isHapticEnabled, context, friendlyName, haptic)
            },
            modifier = Modifier
                .fillMaxWidth(),
            appIcon = {
                Image(
                    asset = iconBitmap ?: CommunityMaterial.Icon.cmd_bookmark,
                    colorFilter = ColorFilter.tint(wearColorPalette.onSurface)
                )
            },
            label = {
                Text(
                    text = friendlyName,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                onEntityClicked(entity.entityId, entity.state)
                                onEntityClickedFeedback(
                                    isToastEnabled,
                                    isHapticEnabled,
                                    context,
                                    friendlyName,
                                    haptic
                                )
                            },
                            onLongPress = {
                                onEntityLongPressed(entity.entityId)
                            }
                        )
                    }
                )
            },
            enabled = entity.state != "unavailable",
            toggleControl = {
                Icon(
                    imageVector = ToggleChipDefaults.switchIcon(isChecked),
                    contentDescription = if (isChecked) {
                        stringResource(R.string.enabled)
                    } else {
                        stringResource(R.string.disabled)
                    }
                )
            },
            colors = WearToggleChip.entityToggleChipBackgroundColors(entity, isChecked)
        )
    } else {
        Chip(
            modifier = Modifier
                .fillMaxWidth(),
            icon = {
                Image(
                    asset = iconBitmap ?: CommunityMaterial.Icon.cmd_bookmark,
                    colorFilter = ColorFilter.tint(wearColorPalette.onSurface)
                )
            },
            label = {
                Text(
                    text = friendlyName,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                onEntityClicked(entity.entityId, entity.state)
                                onEntityClickedFeedback(
                                    isToastEnabled,
                                    isHapticEnabled,
                                    context,
                                    friendlyName,
                                    haptic
                                )
                            },
                            onLongPress = {
                                onEntityLongPressed(entity.entityId)
                            }
                        )
                    }
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

@Preview(device = Devices.WEAR_OS_LARGE_ROUND)
@Composable
private fun PreviewEntityUI() {
    Column {
        EntityUi(
            entity = previewEntity1,
            onEntityClicked = { _, _ -> },
            isHapticEnabled = true,
            isToastEnabled = false,
            onEntityLongPressed = { }
        )
        EntityUi(
            entity = previewEntity3,
            onEntityClicked = { _, _ -> },
            isHapticEnabled = false,
            isToastEnabled = true,
            onEntityLongPressed = { }
        )
    }
}
