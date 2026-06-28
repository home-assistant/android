package io.homeassistant.companion.android.settings.qs.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
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
                    selectedTile = addTileState.tileSlots[1],
                    tileLabel = "Living room",
                    tileSubtitle = "Lights",
                    selectedEntityId = "light.living_room",
                    showResetIcon = true,
                    shouldVibrate = true,
                    submitButtonLabel = commonR.string.tile_save,
                    submitEnabled = true,
                ),
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
            selectedTile = TileSlot(id = "tile_1", name = "Tile 1"),
            servers = emptyList(),
            selectedServerId = 0,
            showServerSelector = false,
            tileLabel = "",
            showSubtitle = true,
            tileSubtitle = "",
            entities = emptyList(),
            selectedEntityId = "",
            entityRegistry = emptyList(),
            deviceRegistry = emptyList(),
            areaRegistry = emptyList(),
            selectedIcon = null,
            showResetIcon = false,
            shouldVibrate = false,
            authRequired = false,
            submitButtonLabel = commonR.string.tile_add,
            submitEnabled = false,
        )

        val multipleServersState = addTileState.copy(
            servers = listOf(
                fakeServer(id = 1, name = "Home"),
                fakeServer(id = 2, name = "Vacation home"),
            ),
            selectedServerId = 1,
            showServerSelector = true,
            tileLabel = "Living room",
            selectedEntityId = "light.living_room",
            submitButtonLabel = commonR.string.tile_save,
            submitEnabled = true,
        )
    }
}
