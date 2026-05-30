package io.homeassistant.companion.android.settings.sensor.views

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
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
    fun `SensorDetailSettingSheetContent with search field and entries`() {
        HAThemeForPreview {
            SensorDetailSettingSheetContent(
                title = "Monitored apps",
                isLoading = false,
                entries = manyEntries(),
                showSearch = true,
                searchQuery = "",
                onQueryChange = {},
                isSelected = { it in setOf("com.spotify.music", "com.netflix.mediaclient") },
                onToggle = { _, _ -> },
                onCancel = {},
                onSave = {},
                modifier = Modifier
                    .height(600.dp)
                    .padding(horizontal = 16.dp),
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `SensorDetailSettingSheetContent without search field`() {
        HAThemeForPreview {
            SensorDetailSettingSheetContent(
                title = "Monitored apps",
                isLoading = false,
                entries = fewEntries(),
                showSearch = false,
                searchQuery = "",
                onQueryChange = {},
                isSelected = { it == "com.google.android.apps.maps" },
                onToggle = { _, _ -> },
                onCancel = {},
                onSave = {},
                modifier = Modifier
                    .height(400.dp)
                    .padding(horizontal = 16.dp),
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `SensorDetailSettingSheetContent loading state`() {
        HAThemeForPreview {
            SensorDetailSettingSheetContent(
                title = "Monitored apps",
                isLoading = true,
                entries = emptyList(),
                showSearch = false,
                searchQuery = "",
                onQueryChange = {},
                isSelected = { false },
                onToggle = { _, _ -> },
                onCancel = {},
                onSave = {},
                modifier = Modifier
                    .height(400.dp)
                    .padding(horizontal = 16.dp),
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `SensorDetailSettingSheetContent empty filtered result`() {
        HAThemeForPreview {
            SensorDetailSettingSheetContent(
                title = "Monitored apps",
                isLoading = false,
                entries = emptyList(),
                showSearch = true,
                searchQuery = "xyz_no_match",
                onQueryChange = {},
                isSelected = { false },
                onToggle = { _, _ -> },
                onCancel = {},
                onSave = {},
                modifier = Modifier
                    .height(600.dp)
                    .padding(horizontal = 16.dp),
            )
        }
    }
}
