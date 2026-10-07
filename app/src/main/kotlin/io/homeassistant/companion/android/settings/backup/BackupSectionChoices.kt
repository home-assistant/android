package io.homeassistant.companion.android.settings.backup

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR

@Composable
internal fun BackupSectionChoices(
    sections: List<BackupSectionSelection>,
    enabled: Boolean,
    onSelect: (BackupSection, Boolean) -> Unit,
) {
    Column {
        sections.forEach { selection ->
            val icon = when (selection.section) {
                BackupSection.AndroidAutoFavorites -> R.drawable.ic_car
                BackupSection.Sensors, BackupSection.SensorOptions -> commonR.drawable.leak
                BackupSection.Connection -> R.drawable.ic_websocket
                BackupSection.Frequency -> R.drawable.ic_clock_fast
            }
            BackupSwitchRow(
                title = stringResource(selection.section.label),
                description = stringResource(selection.section.description),
                icon = icon,
                selected = selection.selected && selection.available,
                enabled = enabled && selection.available,
                onSelect = { onSelect(selection.section, it) },
            )
        }
    }
}
