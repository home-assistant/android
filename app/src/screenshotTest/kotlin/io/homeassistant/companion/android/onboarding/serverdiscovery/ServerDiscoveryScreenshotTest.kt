package io.homeassistant.companion.android.onboarding.serverdiscovery

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import io.homeassistant.companion.android.compose.HAPreviews
import java.net.URL

class ServerDiscoveryScreenshotTest {

    @PreviewTest
    @HAPreviews
    @Composable
    fun `ServerDiscoveryScreen scanning`() {
        HAThemeForPreview {
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
    @HAPreviews
    @Composable
    fun `ServerDiscoveryScreen no server found`() {
        HAThemeForPreview {
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
    @HAPreviews
    @Composable
    fun `ServerDiscoveryScreen with one server found`() {
        HAThemeForPreview {
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
    @HAPreviews
    @Composable
    fun `ServerDiscoveryScreen with multiple servers found`() {
        HAThemeForPreview {
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
                        ServerDiscovered(
                            "Mr Red",
                            URL("http://my.homeassistant.very.long.url.for.testing.with.many.sub.domains.org"),
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
