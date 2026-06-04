@file:OptIn(ExperimentalMaterial3Api::class)

package io.homeassistant.companion.android.settings.sensor.views

import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue.Expanded
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.composable.HAModalBottomSheet
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.util.compose.HAPreviews

class SensorDetailSettingSheetScreenshotTest {

    // 15 app entries so the search field is visible (entries.size > SEARCH_VISIBILITY_THRESHOLD = 10)
    private fun manyEntries() = listOf(
        SettingEntry("com.google.android.apps.maps", "Maps\n(com.google.android.apps.maps)"),
        SettingEntry("com.spotify.music", "Spotify\n(com.spotify.music)"),
        SettingEntry("com.netflix.mediaclient", "Netflix\n(com.netflix.mediaclient)"),
        SettingEntry("com.whatsapp", "WhatsApp\n(com.whatsapp)"),
        SettingEntry("com.slack", "Slack\n(com.slack)"),
        SettingEntry("com.twitter.android", "X (Twitter)\n(com.twitter.android)"),
        SettingEntry("com.instagram.android", "Instagram\n(com.instagram.android)"),
        SettingEntry("com.facebook.katana", "Facebook\n(com.facebook.katana)"),
        SettingEntry("com.google.android.youtube", "YouTube\n(com.google.android.youtube)"),
        SettingEntry("com.amazon.mShop.android.shopping", "Amazon Shopping\n(com.amazon.mShop.android.shopping)"),
        SettingEntry("com.microsoft.teams", "Microsoft Teams\n(com.microsoft.teams)"),
        SettingEntry("com.zoom.videomeetings", "Zoom\n(com.zoom.videomeetings)"),
        SettingEntry("com.dropbox.android", "Dropbox\n(com.dropbox.android)"),
        SettingEntry("com.evernote", "Evernote\n(com.evernote)"),
        SettingEntry("com.todoist", "Todoist\n(com.todoist)"),
    )

    // 4 app entries — below the threshold so no search field is shown
    private fun fewEntries() = listOf(
        SettingEntry("com.google.android.apps.maps", "Maps\n(com.google.android.apps.maps)"),
        SettingEntry("com.spotify.music", "Spotify\n(com.spotify.music)"),
        SettingEntry("com.netflix.mediaclient", "Netflix\n(com.netflix.mediaclient)"),
        SettingEntry("com.whatsapp", "WhatsApp\n(com.whatsapp)"),
    )

    @PreviewTest
    @HAPreviews
    @Composable
    fun `SensorDetailSettingSheet with search field and entries`() {
        PreviewSheet(
            entries = manyEntries(),
            showSearch = true,
            isSelected = { it in setOf("com.spotify.music", "com.netflix.mediaclient") },
        )
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `SensorDetailSettingSheet without search field`() {
        PreviewSheet(
            entries = fewEntries(),
            showSearch = false,
            height = 400.dp,
            isSelected = { it == "com.google.android.apps.maps" },
        )
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `SensorDetailSettingSheet loading state`() {
        PreviewSheet(
            entries = emptyList(),
            showSearch = false,
            isLoading = true,
            height = 400.dp,
        )
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `SensorDetailSettingSheet empty filtered result`() {
        PreviewSheet(
            entries = emptyList(),
            showSearch = true,
            searchQuery = "xyz_no_match",
        )
    }

    @Composable
    private fun PreviewSheet(
        entries: List<SettingEntry>,
        showSearch: Boolean,
        searchQuery: String = "",
        isLoading: Boolean = false,
        height: Dp = 600.dp,
        isSelected: (id: String) -> Boolean = { false },
    ) {
        HAThemeForPreview {
            val density = LocalDensity.current
            HAModalBottomSheet(
                bottomSheetState = SheetState(
                    skipPartiallyExpanded = true,
                    positionalThreshold = { with(density) { 56.dp.toPx() } },
                    velocityThreshold = { with(density) { 125.dp.toPx() } },
                    initialValue = Expanded,
                ),
            ) {
                SensorDetailSettingSheetContent(
                    title = "Monitored apps",
                    isLoading = isLoading,
                    entries = entries,
                    showSearch = showSearch,
                    searchQuery = searchQuery,
                    onQueryChange = {},
                    isSelected = isSelected,
                    onToggle = { _, _ -> },
                    onCancel = {},
                    onSave = {},
                    // Bounds the entry list's weight(1f), mirroring the screen height the real sheet applies.
                    modifier = Modifier.height(height),
                )
            }
        }
    }
}
