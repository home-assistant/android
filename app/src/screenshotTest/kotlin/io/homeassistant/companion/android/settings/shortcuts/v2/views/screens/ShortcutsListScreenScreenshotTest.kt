package io.homeassistant.companion.android.settings.shortcuts.v2.views.screens

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutError
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutSummary
import io.homeassistant.companion.android.settings.shortcuts.v2.AppShortcutItem
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
                    appShortcutItems = emptyList(),
                    homeShortcutItems = emptyList(),
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
        val appItems = mockAppItems(count = 5)
        val homeItems = mockHomeItems(count = 20)
        HAThemeForPreview {
            ShortcutsListScreen(
                state = ShortcutsListState(
                    isLoading = false,
                    maxAppShortcuts = 5,
                    appShortcutItems = appItems,
                    homeShortcutItems = homeItems,
                ),
                dispatch = { _: ShortcutsListAction -> },
                onRetry = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `ShortcutsListScreen content app shortcuts only`() {
        val appItems = mockAppItems(count = 3)
        HAThemeForPreview {
            ShortcutsListScreen(
                state = ShortcutsListState(
                    isLoading = false,
                    maxAppShortcuts = 5,
                    appShortcutItems = appItems,
                    homeShortcutItems = emptyList(),
                ),
                dispatch = { _: ShortcutsListAction -> },
                onRetry = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `ShortcutsListScreen content home shortcuts only`() {
        HAThemeForPreview {
            ShortcutsListScreen(
                state = ShortcutsListState(
                    isLoading = false,
                    appShortcutItems = emptyList(),
                    homeShortcutItems = mockHomeItems(count = 10),
                ),
                dispatch = { _: ShortcutsListAction -> },
                onRetry = {},
            )
        }
    }
}

private fun mockAppItems(count: Int) = List(count) { index ->
    val number = index + 1
    AppShortcutItem(
        index = index,
        summary = ShortcutSummary(
            id = "app_$number",
            selectedIconName = if (index == 0) "mdi:flash" else null,
            label = "Shortcut $number",
        ),
    )
}.toList()

private fun mockHomeItems(count: Int) = List(count) { index ->
    val number = index + 1
    ShortcutSummary(
        id = "home_$number",
        selectedIconName = if (index == 0) "mdi:pin" else null,
        label = if (count == 1) "Home" else "Home $number",
    )
}.toList()
