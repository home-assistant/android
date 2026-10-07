package io.homeassistant.companion.android.settings.backup

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import io.homeassistant.companion.android.common.compose.composable.HASwitch
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme

@Composable
internal fun BackupSwitchRow(
    title: String,
    description: String,
    @DrawableRes icon: Int,
    selected: Boolean,
    enabled: Boolean,
    onSelect: (Boolean) -> Unit,
) {
    BackupPreferenceRow(
        title,
        description,
        icon,
        modifier = Modifier.toggleable(selected, enabled = enabled, role = Role.Switch, onValueChange = onSelect),
    ) {
        HASwitch(
            checked = selected,
            onCheckedChange = onSelect,
            enabled = enabled,
            // The whole row is one accessible switch, including its label and description.
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

@Composable
internal fun BackupPreferenceRow(
    title: String,
    description: String?,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = HADimens.SPACE18).padding(vertical = HADimens.SPACE2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(HADimens.SPACE14)) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = LocalHAColorScheme.current.colorTextLink,
                modifier = Modifier.size(HADimens.SPACE6),
            )
        }
        Column(Modifier.weight(1f).padding(end = HADimens.SPACE2)) {
            Text(
                title,
                style = HATextStyle.Body,
                color = LocalHAColorScheme.current.colorTextPrimary,
                textAlign = TextAlign.Start,
            )
            description?.let { Text(it, style = HATextStyle.BodyMedium, textAlign = TextAlign.Start) }
        }
        trailingContent()
    }
}
