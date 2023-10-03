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
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import androidx.wear.tooling.preview.devices.WearDevices
import com.mikepenz.iconics.compose.Image
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.EntityExt
import io.homeassistant.companion.android.common.data.integration.domain
import io.homeassistant.companion.android.common.data.integration.getIcon
import io.homeassistant.companion.android.common.data.integration.isActive
import io.homeassistant.companion.android.common.util.STATE_UNAVAILABLE
import io.homeassistant.companion.android.theme.wearColorScheme
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

    if (entity.domain in EntityExt.DOMAINS_TOGGLE) {
        val isChecked = entity.isActive()
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
                    asset = iconBitmap,
                    colorFilter = ColorFilter.tint(wearColorScheme.onSurface)
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
            enabled = entity.state != STATE_UNAVAILABLE,
            toggleControl = {
                Icon(
                    imageVector = ToggleChipDefaults.switchIcon(isChecked),
                    contentDescription = if (isChecked) {
                        stringResource(R.string.enabled)
                    } else {
                        stringResource(R.string.disabled)
                    },
                    tint = if (isChecked) wearColorScheme.tertiary else wearColorScheme.onSurface
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
                    asset = iconBitmap,
                    colorFilter = ColorFilter.tint(wearColorScheme.onSurface)
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
            enabled = entity.state != STATE_UNAVAILABLE,
            onClick = {
                onEntityClicked(entity.entityId, entity.state)
                onEntityClickedFeedback(isToastEnabled, isHapticEnabled, context, friendlyName, haptic)
            },
            colors = ChipDefaults.secondaryChipColors()
        )
    }
}

@Preview(device = WearDevices.LARGE_ROUND)
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
