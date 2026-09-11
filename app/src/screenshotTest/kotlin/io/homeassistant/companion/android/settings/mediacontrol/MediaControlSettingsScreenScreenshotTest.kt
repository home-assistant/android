package io.homeassistant.companion.android.settings.mediacontrol

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.Speaker
import io.github.timoptr.mdiicons.generated.Television
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithContext
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithoutContext
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlEntityConfig
import io.homeassistant.companion.android.settings.mediacontrol.views.MediaControlSettingsContent
import io.homeassistant.companion.android.util.compose.HAPreviews

class MediaControlSettingsScreenScreenshotTest {

    /** Resolved with both an area and a device, the longest subtitle an entity can contribute. */
    private val livingRoomConfig = MediaControlEntityConfig(serverId = SERVER_ID, entityId = "media_player.living_room")

    /** Resolved with neither an area nor a device, so the entity contributes no subtitle. */
    private val kitchenConfig = MediaControlEntityConfig(serverId = SERVER_ID, entityId = "media_player.kitchen")

    /** Belongs to a server whose entities are not resolved, so the row falls back to the entity id. */
    private val officeConfig = MediaControlEntityConfig(serverId = OTHER_SERVER_ID, entityId = "media_player.office")

    private val displayState = EntityDisplayState.Loaded(
        listOf(
            EntityDisplayWithContext(
                item = EntityDisplayWithoutContext(
                    entityId = livingRoomConfig.entityId,
                    name = "Living Room TV",
                    icon = Mdi.Television,
                ),
                areaName = "Living Room",
                deviceName = "Sonos One",
            ),
            EntityDisplayWithContext(
                item = EntityDisplayWithoutContext(
                    entityId = kitchenConfig.entityId,
                    name = "Kitchen Radio",
                    icon = Mdi.Speaker,
                ),
            ),
        ),
    )

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

    /** Single server: no row leads with a server name, one entity has a subtitle and one has none. */
    @PreviewTest
    @HAPreviews
    @Composable
    fun `Media control settings with configured entities`() {
        HAThemeForPreview {
            MediaControlSettingsContent(
                uiState = MediaControlSettingsUiState(
                    isLoading = false,
                    mediaControlEntityConfigs = listOf(livingRoomConfig, kitchenConfig),
                    entityDisplayStatePerServer = mapOf(SERVER_ID to displayState),
                ),
                onServerSelected = {},
                onEntitySelected = {},
                onRemoveEntity = {},
            )
        }
    }

    /**
     * Multiple servers: every row leads with its server name, joined to the entity subtitle when
     * there is one, and the entity of the unresolved server falls back to its id.
     */
    @PreviewTest
    @HAPreviews
    @Composable
    fun `Media control settings with multiple servers`() {
        HAThemeForPreview {
            MediaControlSettingsContent(
                uiState = MediaControlSettingsUiState(
                    isLoading = false,
                    serversDropdownItems = listOf(
                        HADropdownItem(key = SERVER_ID, label = "Home"),
                        HADropdownItem(key = OTHER_SERVER_ID, label = "Office"),
                    ),
                    selectedServerId = SERVER_ID,
                    mediaControlEntityConfigs = listOf(livingRoomConfig, kitchenConfig, officeConfig),
                    entityDisplayStatePerServer = mapOf(SERVER_ID to displayState),
                ),
                onServerSelected = {},
                onEntitySelected = {},
                onRemoveEntity = {},
            )
        }
    }
}

private const val SERVER_ID = 1
private const val OTHER_SERVER_ID = 2
