package io.homeassistant.companion.android.settings.sensor.views

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.database.sensor.SensorSetting
import io.homeassistant.companion.android.database.sensor.SensorSettingType
import io.homeassistant.companion.android.settings.sensor.SensorDetailViewModel
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
            entriesSelected = listOf("com.spotify.music", "com.netflix.mediaclient"),
        )
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `SensorDetailSettingSheet without search field`() {
        PreviewSheet(
            entries = fewEntries(),
            entriesSelected = listOf("com.google.android.apps.maps"),
        )
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `SensorDetailSettingSheet loading state`() {
        PreviewSheet(entries = emptyList(), isLoading = true)
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `SensorDetailSettingSheet without entries`() {
        PreviewSheet(entries = emptyList())
    }

    /**
     * Renders [SensorDetailSettingSheet] with a [SheetState] already settled at [SheetValue.Expanded]:
     * the default state starts partially expanded, which a static frame would capture as a half-open
     * sheet.
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun PreviewSheet(
        entries: List<SettingEntry>,
        entriesSelected: List<String> = emptyList(),
        isLoading: Boolean = false,
    ) {
        HAThemeForPreview(modifier = Modifier.fillMaxSize()) {
            SensorDetailSettingSheet(
                title = "Monitored apps",
                state = SensorDetailViewModel.Companion.SettingDialogState(
                    setting = SensorSetting(
                        sensorId = "last_notification",
                        name = "allow_list",
                        value = entriesSelected.joinToString(),
                        valueType = SensorSettingType.LIST_APPS,
                    ),
                    isLoading = isLoading,
                    entries = entries,
                    entriesSelected = entriesSelected,
                ),
                onDismiss = {},
                onSave = {},
                bottomSheetState = SheetState(
                    skipPartiallyExpanded = true,
                    // Thresholds only affect drag gestures, which never happen in screenshots.
                    positionalThreshold = { 0f },
                    velocityThreshold = { 0f },
                    initialValue = SheetValue.Expanded,
                ),
            )
        }
    }
}
