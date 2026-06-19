package io.homeassistant.companion.android.settings.qs.views

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.settings.qs.TileSlot
import io.homeassistant.companion.android.util.compose.HAPreviews

class ManageTilesViewScreenshotTest {

    @PreviewTest
    @HAPreviews
    @Composable
    fun `ManageTilesView add tile`() {
        HAThemeForPreview {
            ManageTilesView(
                snackbarHostState = remember { SnackbarHostState() },
                tileSlots = tileSlots,
                selectedTile = tileSlots.first(),
                onTileSelected = {},
                servers = emptyList(),
                selectedServerId = 0,
                showServerSelector = false,
                onServerSelected = {},
                tileLabel = "",
                onTileLabelChange = {},
                showSubtitle = true,
                tileSubtitle = "",
                onTileSubtitleChange = {},
                entities = emptyList(),
                selectedEntityId = "",
                onEntitySelectedId = {},
                onEntityCleared = {},
                entityRegistry = emptyList(),
                deviceRegistry = emptyList(),
                areaRegistry = emptyList(),
                selectedIcon = null,
                onShowIconDialog = {},
                showResetIcon = false,
                onResetIcon = {},
                shouldVibrate = false,
                onShouldVibrateChange = {},
                authRequired = false,
                onAuthRequiredChange = {},
                submitButtonLabel = commonR.string.tile_add,
                submitEnabled = false,
                onSubmit = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `ManageTilesView update tile`() {
        HAThemeForPreview {
            ManageTilesView(
                snackbarHostState = remember { SnackbarHostState() },
                tileSlots = tileSlots,
                selectedTile = tileSlots[1],
                onTileSelected = {},
                servers = emptyList(),
                selectedServerId = 0,
                showServerSelector = false,
                onServerSelected = {},
                tileLabel = "Living room",
                onTileLabelChange = {},
                showSubtitle = true,
                tileSubtitle = "Lights",
                onTileSubtitleChange = {},
                entities = emptyList(),
                selectedEntityId = "light.living_room",
                onEntitySelectedId = {},
                onEntityCleared = {},
                entityRegistry = emptyList(),
                deviceRegistry = emptyList(),
                areaRegistry = emptyList(),
                selectedIcon = null,
                onShowIconDialog = {},
                showResetIcon = true,
                onResetIcon = {},
                shouldVibrate = true,
                onShouldVibrateChange = {},
                authRequired = false,
                onAuthRequiredChange = {},
                submitButtonLabel = commonR.string.tile_save,
                submitEnabled = true,
                onSubmit = {},
            )
        }
    }

    private companion object {
        val tileSlots = listOf(
            TileSlot(id = "tile_1", name = "Tile 1"),
            TileSlot(id = "tile_2", name = "Tile 2"),
        )
    }
}
