package io.homeassistant.companion.android.onboarding.serverdiscovery

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import io.homeassistant.companion.android.compose.HAThemeScreenshot
import java.net.URL

class ServerDiscoveryScreenshotTest {

    @PreviewTest
    @PreviewLightDark
    @Composable
    fun `ServerDiscoveryScreen scanning`() {
        HAThemeScreenshot {
            ServerDiscoveryScreen(
                discoveryState = Started,
                onConnectClick = {},
                onManualSetupClick = {},
                onHelpClick = {},
                onBackClick = {},
                onDismissOneServerFound = {},
            )
        }
    }

    @PreviewTest
    @PreviewLightDark
    @Composable
    fun `ServerDiscoveryScreen no server found`() {
        HAThemeScreenshot {
            ServerDiscoveryScreen(
                discoveryState = NoServerFound,
                onConnectClick = {},
                onManualSetupClick = {},
                onHelpClick = {},
                onBackClick = {},
                onDismissOneServerFound = {},
            )
        }
    }

    @PreviewTest
    @PreviewLightDark
    @Composable
    fun `ServerDiscoveryScreen with one server found`() {
        HAThemeScreenshot {
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

    @PreviewTest
    @PreviewLightDark
    @Composable
    fun `ServerDiscoveryScreen with multiple servers found`() {
        HAThemeScreenshot {
            ServerDiscoveryScreen(
                discoveryState = ServersDiscovered(
                    listOf(
                        ServerDiscovered(
                            "Mr Yellow",
                            URL("http://my.homeassistant.io"),
                            HomeAssistantVersion(2042, 1, 42),
                        ),
                        ServerDiscovered(
                            "Mr Green",
                            URL("http://ohf.org"),
                            HomeAssistantVersion(2042, 1, 42),
                        ),
                    ),
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
