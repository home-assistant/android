package io.homeassistant.companion.android.settings.shortcuts.v2.views.selector

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutTargetValue
import io.homeassistant.companion.android.settings.shortcuts.v2.views.preview.ShortcutPreviewData
import io.homeassistant.companion.android.settings.shortcuts.v2.views.screens.ShortcutEditorScreenState
import io.homeassistant.companion.android.util.compose.entity.EntityPicker
import kotlinx.collections.immutable.persistentListOf

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun ShortcutTargetInput(
    target: ShortcutTargetValue,
    screen: ShortcutEditorScreenState,
    serverId: Int,
    onTargetChange: (ShortcutTargetValue) -> Unit,
) {
    when (target) {
        is ShortcutTargetValue.Lovelace -> {
            val bringIntoViewRequester = remember { BringIntoViewRequester() }

            HATextField(
                value = target.path,
                onValueChange = { onTargetChange(ShortcutTargetValue.Lovelace(it)) },
                label = { Text(stringResource(R.string.lovelace_view_dashboard)) },
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Uri,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(bringIntoViewRequester),
            )
        }

        is ShortcutTargetValue.Entity -> {
            val selectedEntityId = target.entityId.takeIf { it.isNotBlank() }
            val entities = screen.entities[serverId] ?: persistentListOf()
            val entityRegistry = screen.entityRegistry[serverId]
            val deviceRegistry = screen.deviceRegistry[serverId]
            val areaRegistry = screen.areaRegistry[serverId]
            EntityPicker(
                entities = entities,
                entityRegistry = entityRegistry,
                deviceRegistry = deviceRegistry,
                areaRegistry = areaRegistry,
                selectedEntityId = selectedEntityId,
                onEntitySelectedId = { entityId ->
                    onTargetChange(ShortcutTargetValue.Entity(entityId))
                },
                onEntityCleared = {
                    onTargetChange(ShortcutTargetValue.Entity(""))
                },
            )
        }
    }
}

@Preview(name = "Shortcut Target - Lovelace")
@Composable
private fun ShortcutTargetInputLovelacePreview() {
    HAThemeForPreview {
        ShortcutTargetInput(
            target = ShortcutTargetValue.Lovelace("/lovelace/main"),
            screen = ShortcutPreviewData.buildScreenState(),
            serverId = 1,
            onTargetChange = {},
        )
    }
}

@Preview(name = "Shortcut Target - Entity")
@Composable
private fun ShortcutTargetInputEntityPreview() {
    HAThemeForPreview {
        ShortcutTargetInput(
            target = ShortcutTargetValue.Entity("light.living_room"),
            screen = ShortcutPreviewData.buildScreenState(
                entities = ShortcutPreviewData.previewEntitiesByServer,
                entityRegistry = ShortcutPreviewData.previewEntityRegistryByServer,
                deviceRegistry = ShortcutPreviewData.previewDeviceRegistryByServer,
                areaRegistry = ShortcutPreviewData.previewAreaRegistryByServer,
            ),
            serverId = 1,
            onTargetChange = {},
        )
    }
}
