package io.homeassistant.companion.android.settings.shortcuts.v2.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.settings.views.EmptyState

@Composable
internal fun EmptyStateContent(hasServers: Boolean) {
    EmptyState(
        icon = CommunityMaterial.Icon2.cmd_flash,
        title = stringResource(R.string.shortcuts_empty_title),
        subtitle = if (hasServers) {
            stringResource(R.string.shortcuts_empty_subtitle)
        } else {
            stringResource(R.string.shortcut_no_servers)
        },
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

@Preview(name = "Empty State Content - Has Servers")
@Composable
private fun EmptyStateContentHasServersPreview() {
    HAThemeForPreview {
        EmptyStateContent(hasServers = true)
    }
}

@Preview(name = "Empty State Content - No Servers")
@Composable
private fun EmptyStateContentNoServersPreview() {
    HAThemeForPreview {
        EmptyStateContent(hasServers = false)
    }
}
