package io.homeassistant.companion.android.settings.shortcuts.v2.ui.screens

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutTargetValue
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutType
import io.homeassistant.companion.android.settings.shortcuts.v2.ShortcutEditorUiState
import io.homeassistant.companion.android.settings.shortcuts.v2.ShortcutsListAction
import io.homeassistant.companion.android.settings.shortcuts.v2.ui.preview.ShortcutPreviewData
import io.homeassistant.companion.android.util.compose.HAPreviews
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

class ShortcutsV2ScreenshotTest {

    @PreviewTest
    @HAPreviews
    @Composable
    fun `Shortcuts loading`() {
        HAThemeForPreview {
            ShortcutsScreen(
                state = ShortcutPreviewData.buildListState(isLoading = true),
                dispatch = { _: ShortcutsListAction -> },
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `Shortcuts empty no servers`() {
        HAThemeForPreview {
            ShortcutsScreen(
                state = ShortcutPreviewData.buildListState(
                    dynamicSummaries = persistentListOf(),
                    pinnedSummaries = persistentListOf(),
                    servers = persistentListOf(),
                ),
                dispatch = { _: ShortcutsListAction -> },
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `Shortcuts content max`() {
        val dynamicSummaries = ShortcutPreviewData.buildDynamicSummaries(
            count = 5,
            type = ShortcutType.LOVELACE,
            createdIndex = 0,
        ).map { summary ->
            summary.copy(isCreated = true)
        }.toImmutableList()
        val basePinned = ShortcutPreviewData.buildPinnedSummaries().first()
        val pinnedSummaries = List(20) { index ->
            val number = index + 1
            basePinned.copy(
                id = "pinned_$number",
                label = "Pinned $number",
                description = "Pinned shortcut $number",
                target = ShortcutTargetValue.Lovelace("/lovelace/pinned$number"),
            )
        }.toImmutableList()
        HAThemeForPreview {
            ShortcutsScreen(
                state = ShortcutPreviewData.buildListState(
                    dynamicSummaries = dynamicSummaries,
                    pinnedSummaries = pinnedSummaries,
                ),
                dispatch = { _: ShortcutsListAction -> },
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `Shortcuts content dynamic only`() {
        val dynamicSummaries = ShortcutPreviewData.buildDynamicSummaries(
            count = 3,
            type = ShortcutType.LOVELACE,
            createdIndex = 0,
        ).map { summary ->
            summary.copy(isCreated = true)
        }.toImmutableList()
        HAThemeForPreview {
            ShortcutsScreen(
                state = ShortcutPreviewData.buildListState(
                    dynamicSummaries = dynamicSummaries,
                    pinnedSummaries = persistentListOf(),
                ),
                dispatch = { _: ShortcutsListAction -> },
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `Shortcuts content pinned only`() {
        HAThemeForPreview {
            ShortcutsScreen(
                state = ShortcutPreviewData.buildListState(
                    dynamicSummaries = persistentListOf(),
                    pinnedSummaries = ShortcutPreviewData.buildPinnedSummaries(),
                ),
                dispatch = { _: ShortcutsListAction -> },
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `DynamicShortcutEditor loading`() {
        HAThemeForPreview {
            ShortcutEditorScreen(
                state = ShortcutEditorUiState(
                    screen = ShortcutPreviewData.buildScreenState(isLoading = true),
                    editor = ShortcutPreviewData.buildDynamicEditorState(),
                ),
                dispatch = { _: ShortcutEditAction -> },
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `DynamicShortcutEditor entity target`() {
        HAThemeForPreview {
            ShortcutEditorScreen(
                state = ShortcutEditorUiState(
                    screen = ShortcutPreviewData.buildScreenState(
                        entities = ShortcutPreviewData.previewEntitiesByServer,
                        entityRegistry = ShortcutPreviewData.previewEntityRegistryByServer,
                        deviceRegistry = ShortcutPreviewData.previewDeviceRegistryByServer,
                        areaRegistry = ShortcutPreviewData.previewAreaRegistryByServer,
                    ),
                    editor = ShortcutPreviewData.buildDynamicEditorState(
                        draftSeed = ShortcutPreviewData.buildDraft(
                            type = ShortcutType.ENTITY_ID,
                            id = ShortcutPreviewData.dynamicShortcutId(0),
                        ),
                        isEditing = true,
                    ),
                ),
                dispatch = { _: ShortcutEditAction -> },
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `PinnedShortcutEditor loading`() {
        HAThemeForPreview {
            ShortcutEditorScreen(
                state = ShortcutEditorUiState(
                    screen = ShortcutPreviewData.buildScreenState(isLoading = true),
                    editor = ShortcutPreviewData.buildPinnedEditorState(),
                ),
                dispatch = { _: ShortcutEditAction -> },
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `PinnedShortcutEditor default`() {
        HAThemeForPreview {
            ShortcutEditorScreen(
                state = ShortcutEditorUiState(
                    screen = ShortcutPreviewData.buildScreenState(),
                    editor = ShortcutPreviewData.buildPinnedEditorState(
                        pinnedDraft = ShortcutPreviewData.buildPinnedDraft(),
                        isEditing = true,
                    ),
                ),
                dispatch = { _: ShortcutEditAction -> },
            )
        }
    }
}
