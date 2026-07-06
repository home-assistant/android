package io.homeassistant.companion.android.settings.mediacontrol

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlEntityConfig
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.homeassistant.companion.android.settings.mediacontrol.views.MediaControlSettingsContent
import io.homeassistant.companion.android.util.compose.HAPreviews

class MediaControlSettingsScreenScreenshotTest {

    private val livingRoomConfig = MediaControlEntityConfig(serverId = 1, entityId = "media_player.living_room")
    private val bedroomConfig = MediaControlEntityConfig(serverId = 1, entityId = "media_player.bedroom")

    @PreviewTest
    @HAPreviews
    @Composable
    fun `Media control settings is loading`() {
        HAThemeForPreview {
            MediaControlSettingsContent(
                uiState = MediaControlSettingsUiState(isLoading = true),
                onServerSelected = {},
                onEntitySelected = {},
                onRemoveEntity = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `Media control settings empty`() {
        HAThemeForPreview {
            MediaControlSettingsContent(
                uiState = MediaControlSettingsUiState(isLoading = false),
                onServerSelected = {},
                onEntitySelected = {},
                onRemoveEntity = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `Media control settings with configured entities`() {
        HAThemeForPreview {
            MediaControlSettingsContent(
                uiState = MediaControlSettingsUiState(
                    isLoading = false,
                    configuredEntityItems = listOf(
                        ConfiguredEntityItem(
                            config = livingRoomConfig,
                            name = "Living Room TV",
                            entity = null,
                        ),
                        ConfiguredEntityItem(
                            config = bedroomConfig,
                            name = "Bedroom Speaker",
                            entity = null,
                        ),
                    ),
                ),
                onServerSelected = {},
                onEntitySelected = {},
                onRemoveEntity = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `Media control settings with multiple servers`() {
        HAThemeForPreview {
            MediaControlSettingsContent(
                uiState = MediaControlSettingsUiState(
                    isLoading = false,
                    servers = listOf(
                        Server(
                            id = 1,
                            _name = "Home",
                            connection = ServerConnectionInfo(externalUrl = "http://home.local"),
                            session = ServerSessionInfo(),
                            user = ServerUserInfo(),
                        ),
                        Server(
                            id = 2,
                            _name = "Office",
                            connection = ServerConnectionInfo(externalUrl = "http://office.local"),
                            session = ServerSessionInfo(),
                            user = ServerUserInfo(),
                        ),
                    ),
                    selectedServerId = 1,
                    configuredEntityItems = listOf(
                        ConfiguredEntityItem(
                            config = livingRoomConfig,
                            name = "Living Room TV",
                            entity = null,
                        ),
                    ),
                ),
                onServerSelected = {},
                onEntitySelected = {},
                onRemoveEntity = {},
            )
        }
    }
}
