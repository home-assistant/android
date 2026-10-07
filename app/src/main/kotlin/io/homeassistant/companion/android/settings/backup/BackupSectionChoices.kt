package io.homeassistant.companion.android.settings.backup

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

@Composable
internal fun BackupSectionChoices(
    sections: List<BackupSectionSelection>,
    enabled: Boolean,
    onSelect: (BackupSection, Boolean) -> Unit,
) {
    Column {
        sections.forEach { selection ->
            BackupSwitchRow(
                title = stringResource(selection.section.label),
                description = stringResource(selection.section.description),
                icon = selection.section.icon,
                selected = selection.selected && selection.available,
                enabled = enabled && selection.available,
                onSelect = { onSelect(selection.section, it) },
            )
        }
    }
}
