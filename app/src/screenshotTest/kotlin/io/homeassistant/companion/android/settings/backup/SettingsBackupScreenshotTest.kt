package io.homeassistant.companion.android.settings.backup

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.data.backup.BackupServerData
import io.homeassistant.companion.android.common.data.backup.SETTINGS_BACKUP_FORMAT
import io.homeassistant.companion.android.common.data.backup.SETTINGS_BACKUP_VERSION
import io.homeassistant.companion.android.common.data.backup.SettingsBackupData
import io.homeassistant.companion.android.util.compose.HAPreviews
import kotlin.time.Instant

class SettingsBackupScreenshotTest {
    @PreviewTest
    @HAPreviews
    @Composable
    fun `Backup home without servers`() {
        HAThemeForPreview {
            BackupHomeContent(SettingsBackupUiState.Content(emptyList()), { _, _ -> }, {}, {}, {})
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `Restore with multiple servers`() {
        val backup = SettingsBackupData(
            SETTINGS_BACKUP_FORMAT,
            SETTINGS_BACKUP_VERSION,
            "2026.10.2",
            Instant.parse("2026-10-07T12:00:00Z"),
            listOf(BackupServerData("home", "Home"), BackupServerData("cabin", "Cabin")),
            favorites = emptyList(),
        )
        HAThemeForPreview {
            BackupRestoreContent(
                SettingsBackupUiState.Content(
                    listOf(BackupDestination(42, "Home server"), BackupDestination(99, "Cabin server")),
                    restore = RestoreDraft(backup, mapOf("home" to BackupServerTarget.Server(42))),
                ),
                { _, _ -> },
                { _, _ -> },
                {},
                {},
                {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `Backup loading`() {
        HAThemeForPreview { HALoading() }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `Backup error`() {
        HAThemeForPreview { BackupErrorContent {} }
    }
}
