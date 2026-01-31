package io.homeassistant.companion.android.settings.shortcuts.v2.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.settings.views.EmptyState

// TODO: Keep separate for clarity for now, but this should be improved later.
@Composable
internal fun EmptyStateContent() {
    EmptyState(
        icon = CommunityMaterial.Icon2.cmd_flash,
        title = stringResource(R.string.shortcuts_empty_title),
        subtitle = stringResource(R.string.shortcuts_empty_subtitle),
    )
}

@Composable
internal fun EmptyStateNoServers() {
    EmptyState(
        icon = CommunityMaterial.Icon2.cmd_flash,
        title = stringResource(R.string.shortcuts_empty_title),
        subtitle = stringResource(R.string.shortcut_no_servers),
    )
}

@Composable
internal fun EmptyStateContentSlots() {
    EmptyState(
        icon = CommunityMaterial.Icon2.cmd_flash,
        title = stringResource(R.string.state_unavailable),
        subtitle = stringResource(R.string.shortcut_dynamic_slots_full),
    )
}

@Composable
internal fun NotSupportedStateContent() {
    EmptyState(
        icon = CommunityMaterial.Icon.cmd_alert,
        title = stringResource(R.string.failed_unsupported),
        subtitle = stringResource(R.string.shortcuts_not_supported_subtitle),
    )
}

@Preview(name = "Empty State Content")
@Composable
private fun EmptyStateContentPreview() {
    HAThemeForPreview {
        EmptyStateContent()
    }
}

@Preview(name = "Empty State Content - No Servers")
@Composable
private fun EmptyStateContentNoServersPreview() {
    HAThemeForPreview {
        EmptyStateNoServers()
    }
}

@Preview(name = "Empty State Content - Slots Full")
@Composable
private fun EmptyStateContentSlotsPreview() {
    HAThemeForPreview {
        EmptyStateContentSlots()
    }
}

@Preview(name = "Empty State Content - Not Supported")
@Composable
private fun NotSupportedStateContentPreview() {
    HAThemeForPreview {
        NotSupportedStateContent()
    }
}
