package io.homeassistant.companion.android.settings.shortcuts.v2.views.screens

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutError
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutSummary
import io.homeassistant.companion.android.settings.shortcuts.v2.DynamicShortcutItem
import io.homeassistant.companion.android.settings.shortcuts.v2.ShortcutsListAction
import io.homeassistant.companion.android.settings.shortcuts.v2.ShortcutsListState
import io.homeassistant.companion.android.util.compose.HAPreviews

class ShortcutsListScreenScreenshotTest {

    @PreviewTest
    @HAPreviews
    @Composable
    fun `ShortcutsListScreen loading`() {
        HAThemeForPreview {
            ShortcutsListScreen(
                state = ShortcutsListState(isLoading = true),
                dispatch = { _: ShortcutsListAction -> },
                onRetry = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `ShortcutsListScreen empty`() {
        HAThemeForPreview {
            ShortcutsListScreen(
                state = ShortcutsListState(
                    isLoading = false,
                    dynamicItems = emptyList(),
                    pinnedItems = emptyList(),
                ),
                dispatch = { _: ShortcutsListAction -> },
                onRetry = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `ShortcutsListScreen error`() {
        HAThemeForPreview {
            ShortcutsListScreen(
                state = ShortcutsListState(isLoading = false, error = ShortcutError.Unknown),
                dispatch = { _: ShortcutsListAction -> },
                onRetry = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `ShortcutsListScreen content max`() {
        val dynamicItems = mockDynamicItems(count = 5)
        val pinnedItems = mockPinnedItems(count = 20)
        HAThemeForPreview {
            ShortcutsListScreen(
                state = ShortcutsListState(
                    isLoading = false,
                    maxDynamicShortcuts = 5,
                    dynamicItems = dynamicItems,
                    pinnedItems = pinnedItems,
                ),
                dispatch = { _: ShortcutsListAction -> },
                onRetry = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `ShortcutsListScreen content dynamic only`() {
        val dynamicItems = mockDynamicItems(count = 3)
        HAThemeForPreview {
            ShortcutsListScreen(
                state = ShortcutsListState(
                    isLoading = false,
                    maxDynamicShortcuts = 5,
                    dynamicItems = dynamicItems,
                    pinnedItems = emptyList(),
                ),
                dispatch = { _: ShortcutsListAction -> },
                onRetry = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `ShortcutsListScreen content pinned only`() {
        HAThemeForPreview {
            ShortcutsListScreen(
                state = ShortcutsListState(
                    isLoading = false,
                    dynamicItems = emptyList(),
                    pinnedItems = mockPinnedItems(count = 1),
                ),
                dispatch = { _: ShortcutsListAction -> },
                onRetry = {},
            )
        }
    }
}

private fun mockDynamicItems(count: Int) = List(count) { index ->
    val number = index + 1
    DynamicShortcutItem(
        index = index,
        summary = ShortcutSummary(
            id = "dynamic_$number",
            selectedIconName = if (index == 0) "mdi:flash" else null,
            label = "Shortcut $number",
        ),
    )
}.toList()

private fun mockPinnedItems(count: Int) = List(count) { index ->
    val number = index + 1
    ShortcutSummary(
        id = "pinned_$number",
        selectedIconName = if (index == 0) "mdi:pin" else null,
        label = if (count == 1) "Pinned" else "Pinned $number",
    )
}.toList()
