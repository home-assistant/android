package io.homeassistant.companion.android.settings.shortcuts.v2.ui.selector

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
import com.mikepenz.iconics.typeface.IIcon
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme

@Composable
internal fun ShortcutIconPicker(selectedIcon: IIcon?, onIconClick: () -> Unit) {
    OutlinedButton(onClick = onIconClick) {
        val painter = if (selectedIcon != null) {
            remember(selectedIcon) { IconicsPainter(selectedIcon) }
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
            selectedIcon = null,
            onIconClick = {},
        )
    }
}
