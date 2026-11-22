package io.homeassistant.companion.android.launch.link

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.frontend.navigation.FrontendTarget
import io.homeassistant.companion.android.settings.server.ServerChooserItem
import io.homeassistant.companion.android.util.compose.HAPreviews

class LinkActivityScreenshotTest {

    @PreviewTest
    @HAPreviews
    @Composable
    fun `LinkActivity default screen`() {
        HAThemeForPreview {
            LinkActivityScreen(
                uiState = LinkUiState.Loading,
                onServerSelected = {},
                onServerChooserDismissed = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `LinkActivity choosing server`() {
        HAThemeForPreview {
            LinkActivityScreen(
                uiState = LinkUiState.ChoosingServer(
                    items = listOf(
                        ServerChooserItem(serverId = 1, userName = "Alice Smith", serverName = "Home"),
                        ServerChooserItem(serverId = 2, userName = "Bob", serverName = "Friends home", isActive = true),
                    ),
                    target = FrontendTarget.Path("/lovelace"),
                ),
                onServerSelected = {},
                onServerChooserDismissed = {},
            )
        }
    }
}
