package io.homeassistant.companion.android.settings.qs.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.android.tools.screenshot.PreviewTest
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.settings.qs.ManageTilesState
import io.homeassistant.companion.android.util.compose.HAPreviews
import io.homeassistant.companion.android.util.icondialog.getIconByMdiName

class ManageTilesScreenshotTest {

    @PreviewTest
    @HAPreviews
    @Composable
    fun `ManageTiles add tile`() {
        HAThemeForPreview {
            ManageTilesContent(
                snackbarHostState = remember { SnackbarHostState() },
                state = addTileState,
                submitEnabled = false,
                onTileSelected = {},
                onServerSelected = {},
                onTileLabelChange = {},
                onTileSubtitleChange = {},
                onSelectionChanged = {},
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
            ManageTilesContent(
                snackbarHostState = remember { SnackbarHostState() },
                state = addTileState.copy(
                    selectedTileId = addTileState.tileSlotsDropdownItems[1].key,
                    serversDropdownItems = listOf(
                        HADropdownItem(key = 1, label = "Home"),
                        HADropdownItem(key = 2, label = "Vacation home"),
                    ),
                    selectedServerId = 1,
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
                onSelectionChanged = {},
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
    fun `ManageTiles icon selected`() {
        HAThemeForPreview {
            ManageTilesContent(
                snackbarHostState = remember { SnackbarHostState() },
                state = addTileState.copy(
                    selectedIconId = "mdi:account",
                    selectedIcon = CommunityMaterial.getIconByMdiName("mdi:account"),
                    selectedEntityId = "light.living_room",
                ),
                submitEnabled = false,
                onTileSelected = {},
                onServerSelected = {},
                onTileLabelChange = {},
                onTileSubtitleChange = {},
                onSelectionChanged = {},
                onShowIconDialog = {},
                onResetIcon = {},
                onShouldVibrateChange = {},
                onAuthRequiredChange = {},
                onSubmit = {},
            )
        }
    }

    private companion object {
        val addTileState = ManageTilesState(
            tileSlotsDropdownItems = listOf(
                HADropdownItem(key = "tile_1", label = "Tile 1"),
                HADropdownItem(key = "tile_2", label = "Tile 2"),
            ),
            selectedTileId = "tile_1",
            serversDropdownItems = listOf(HADropdownItem(key = 1, label = "Home")),
            selectedServerId = 1,
            tileLabel = "",
            // Non-blank showSubtitle + empty tileSubtitle demonstrates the subtitle field is
            // rendered (and empty) for a brand-new tile, validating the subtitle-null bug fix.
            showSubtitle = true,
            tileSubtitle = "",
            selectedEntityId = "",
            selectedIcon = null,
            submitButtonLabel = commonR.string.tile_add,
        )
    }
}
