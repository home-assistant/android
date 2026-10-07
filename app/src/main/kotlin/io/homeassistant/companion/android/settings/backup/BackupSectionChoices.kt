package io.homeassistant.companion.android.settings.backup

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import io.homeassistant.companion.android.common.compose.composable.HACheckbox
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle

@Composable
internal fun BackupSectionChoices(
    sections: List<BackupSectionSelection>,
    enabled: Boolean,
    onSelect: (BackupSection, Boolean) -> Unit,
) {
    sections.forEach { selection ->
        Row(
            modifier = Modifier.fillMaxWidth().toggleable(
                value = selection.selected && selection.available,
                enabled = enabled && selection.available,
                role = Role.Checkbox,
                onValueChange = { onSelect(selection.section, it) },
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HACheckbox(
                checked = selection.selected && selection.available,
                onCheckedChange = null,
                enabled = enabled && selection.available,
            )
            Text(
                text = stringResource(selection.section.label),
                style = HATextStyle.Body,
                modifier = Modifier.padding(start = HADimens.SPACE2),
            )
        }
    }
}
