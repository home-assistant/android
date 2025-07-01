package io.homeassistant.companion.android.onboarding.serverdiscovery

import androidx.compose.runtime.Composable
import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import io.homeassistant.companion.android.compose.HAPreviews
import io.homeassistant.companion.android.onboarding.theme.HATheme
import java.net.URL

class ServerDiscoveryScreenScreenshotTest {

    @HAPreviews
    @Composable
    private fun ServerDiscoveryScreen_discovering_Preview() {
        HATheme {
            ServerDiscoveryScreen()
        }
    }

    @HAPreviews
    @Composable
    fun ServerDiscoveryScreen_with_one_server_Preview() {
        HATheme {
            ServerDiscoveryScreen(
                discoveryState = ServerDiscovered(
                    "hello",
                    URL("http://my.homeassistant.io"),
                    HomeAssistantVersion(2042, 1, 42),
                ),
                onConnectClick = {},
                onManualSetupClick = {},
                onHelpClick = {},
                onBackClick = {},
                onDismissOneServerFound = {},
            )
        }
    }
}
