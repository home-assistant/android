package io.homeassistant.companion.android.settings.mediacontrol

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlEntityConfig
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
                onMove = { _, _ -> },
                onReorderComplete = {},
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
                onMove = { _, _ -> },
                onReorderComplete = {},
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
                    configuredEntities = listOf(livingRoomConfig, bedroomConfig),
                    entityNamesByConfig = mapOf(
                        livingRoomConfig to "Living Room TV",
                        bedroomConfig to "Bedroom Speaker",
                    ),
                ),
                onServerSelected = {},
                onEntitySelected = {},
                onRemoveEntity = {},
                onMove = { _, _ -> },
                onReorderComplete = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `Media control settings with configured entities and icons`() {
        HAThemeForPreview {
            MediaControlSettingsContent(
                uiState = MediaControlSettingsUiState(
                    isLoading = false,
                    configuredEntities = listOf(livingRoomConfig, bedroomConfig),
                    entityNamesByConfig = mapOf(
                        livingRoomConfig to "Living Room TV",
                        bedroomConfig to "Bedroom Speaker",
                    ),
                    entityIconsByConfig = mapOf(
                        livingRoomConfig to CommunityMaterial.Icon.cmd_cast,
                        bedroomConfig to CommunityMaterial.Icon.cmd_cast,
                    ),
                ),
                onServerSelected = {},
                onEntitySelected = {},
                onRemoveEntity = {},
                onMove = { _, _ -> },
                onReorderComplete = {},
            )
        }
    }
}
