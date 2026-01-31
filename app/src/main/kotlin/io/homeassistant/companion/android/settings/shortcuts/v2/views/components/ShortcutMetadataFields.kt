package io.homeassistant.companion.android.settings.shortcuts.v2.views.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutDraft
import io.homeassistant.companion.android.settings.shortcuts.v2.views.preview.ShortcutPreviewData
import io.homeassistant.companion.android.settings.shortcuts.v2.views.screens.ShortcutEditorScreenState
import io.homeassistant.companion.android.util.compose.ServerExposedDropdownMenu

@Composable
internal fun ShortcutMetadataFields(
    draft: ShortcutDraft,
    labelText: String,
    descriptionText: String,
    screen: ShortcutEditorScreenState,
    onLabelChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onServerChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
        HATextField(
            value = draft.label,
            onValueChange = onLabelChange,
            label = {
                Text(labelText)
            },
            modifier = Modifier.fillMaxWidth(),
        )

        HATextField(
            value = draft.description,
            onValueChange = onDescriptionChange,
            label = {
                Text(descriptionText)
            },
            modifier = Modifier.fillMaxWidth(),
        )

        // TODO: Change to Bottom sheet
        if (screen.servers.isNotEmpty() &&
            (screen.servers.size > 1 || screen.servers.none { it.id == draft.serverId })
        ) {
            ServerExposedDropdownMenu(
                servers = screen.servers,
                current = draft.serverId,
                onSelected = onServerChange,
            )
        }
    }
}

@Preview(name = "Shortcut Metadata Fields")
@Composable
private fun ShortcutMetadataFieldsPreview() {
    HAThemeForPreview {
        ShortcutMetadataFields(
            draft = ShortcutPreviewData.buildDraft(),
            labelText = "Label",
            descriptionText = "Description",
            screen = ShortcutPreviewData.buildScreenState(servers = ShortcutPreviewData.previewServers),
            onLabelChange = {},
            onDescriptionChange = {},
            onServerChange = {},
        )
    }
}
