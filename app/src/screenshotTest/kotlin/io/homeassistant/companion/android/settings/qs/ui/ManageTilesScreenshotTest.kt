package io.homeassistant.companion.android.settings.qs.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.homeassistant.companion.android.settings.qs.ManageTilesState
import io.homeassistant.companion.android.settings.qs.TileSlot
import io.homeassistant.companion.android.util.compose.HAPreviews

class ManageTilesScreenshotTest {

    @PreviewTest
    @HAPreviews
    @Composable
    fun `ManageTiles add tile`() {
        HAThemeForPreview {
            ManageTiles(
                snackbarHostState = remember { SnackbarHostState() },
                state = addTileState,
                submitEnabled = false,
                onTileSelected = {},
                onServerSelected = {},
                onTileLabelChange = {},
                onTileSubtitleChange = {},
                onEntitySelectedId = {},
                onEntityCleared = {},
                onShowIconDialog = {},
                onResetIcon = {},
                onShouldVibrateChange = {},
                onAuthRequiredChange = {},
                onSubmit = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `ManageTiles update tile`() {
        HAThemeForPreview {
            ManageTiles(
                snackbarHostState = remember { SnackbarHostState() },
                state = addTileState.copy(
                    selectedTileId = addTileState.tileSlots[1].id,
                    tileLabel = "Living room",
                    tileSubtitle = "Lights",
                    selectedEntityId = "light.living_room",
                    submitButtonLabel = commonR.string.tile_save,
                ),
                submitEnabled = true,
                onTileSelected = {},
                onServerSelected = {},
                onTileLabelChange = {},
                onTileSubtitleChange = {},
                onEntitySelectedId = {},
                onEntityCleared = {},
                onShowIconDialog = {},
                onResetIcon = {},
                onShouldVibrateChange = {},
                onAuthRequiredChange = {},
                onSubmit = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `ManageTiles multiple servers`() {
        HAThemeForPreview {
            ManageTiles(
                snackbarHostState = remember { SnackbarHostState() },
                state = multipleServersState,
                submitEnabled = false,
                onTileSelected = {},
                onServerSelected = {},
                onTileLabelChange = {},
                onTileSubtitleChange = {},
                onEntitySelectedId = {},
                onEntityCleared = {},
                onShowIconDialog = {},
                onResetIcon = {},
                onShouldVibrateChange = {},
                onAuthRequiredChange = {},
                onSubmit = {},
            )
        }
    }

    private companion object {
        fun fakeServer(id: Int, name: String) = Server(
            id = id,
            _name = name,
            connection = ServerConnectionInfo(externalUrl = "https://example.com"),
            session = ServerSessionInfo(),
            user = ServerUserInfo(),
        )

        val addTileState = ManageTilesState(
            tileSlots = listOf(
                TileSlot(id = "tile_1", name = "Tile 1"),
                TileSlot(id = "tile_2", name = "Tile 2"),
            ),
            selectedTileId = "tile_1",
            servers = listOf(
                fakeServer(id = 1, name = "Home"),
            ),
            serversDropdownItems = listOf(
                HADropdownItem(1, "Home"),
            ),
            selectedServerId = 1,
            tileLabel = "",
            tileSubtitle = "",
            selectedEntityId = "",
            entityRegistry = emptyList(),
            deviceRegistry = emptyList(),
            areaRegistry = emptyList(),
            selectedIcon = null,
            submitButtonLabel = commonR.string.tile_add,
        )

        val multipleServersState = addTileState.copy(
            servers = listOf(
                fakeServer(id = 1, name = "Home"),
                fakeServer(id = 2, name = "Vacation home"),
            ),
            serversDropdownItems = listOf(
                HADropdownItem(1, "Home"),
                HADropdownItem(2, "Vacation home"),
            ),
            selectedServerId = 1,
            tileLabel = "Living room",
            selectedEntityId = "light.living_room",
            submitButtonLabel = commonR.string.tile_save,
        )
    }
}
