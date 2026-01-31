package io.homeassistant.companion.android.settings.shortcuts.v2.views.selector

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.mikepenz.iconics.compose.IconicsPainter
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.util.icondialog.getIconByMdiName

@Composable
internal fun ShortcutIconPicker(selectedIconName: String?, onIconClick: () -> Unit) {
    OutlinedButton(onClick = onIconClick) {
        val icon = remember(selectedIconName) {
            selectedIconName?.let(CommunityMaterial::getIconByMdiName)
        }
        val painter = if (icon != null) {
            remember(icon) { IconicsPainter(icon) }
        } else {
            painterResource(R.drawable.ic_stat_ic_notification_blue)
        }

        Image(
            painter = painter,
            contentDescription = stringResource(R.string.shortcut_icon),
            modifier = Modifier.size(HADimens.SPACE6),
            colorFilter = ColorFilter.tint(LocalHAColorScheme.current.colorFillPrimaryLoudResting),
        )
    }
}

@Preview(name = "Shortcut Icon Picker")
@Composable
private fun ShortcutIconPickerPreview() {
    HAThemeForPreview {
        ShortcutIconPicker(
            selectedIconName = null,
            onIconClick = {},
        )
    }
}
