package io.homeassistant.companion.android.settings.backup

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.data.backup.BackupEntityReference
import io.homeassistant.companion.android.common.data.backup.BackupSections
import io.homeassistant.companion.android.common.data.backup.BackupServerData
import io.homeassistant.companion.android.common.data.backup.BackupSettingsChanges
import io.homeassistant.companion.android.common.data.backup.RestoredSensorSelection
import io.homeassistant.companion.android.common.data.backup.SETTINGS_BACKUP_FORMAT
import io.homeassistant.companion.android.common.data.backup.SETTINGS_BACKUP_VERSION
import io.homeassistant.companion.android.common.data.backup.SettingsBackupData
import io.homeassistant.companion.android.common.data.prefs.AutoFavorite
import io.homeassistant.companion.android.database.sensor.SensorSetting
import io.homeassistant.companion.android.database.sensor.SensorSettingType
import io.homeassistant.companion.android.database.settings.SensorUpdateFrequencySetting
import io.homeassistant.companion.android.database.settings.WebsocketSetting
import io.homeassistant.companion.android.util.compose.HAPreviews
import kotlin.time.Instant

class SettingsBackupScreenshotTest {
    @PreviewTest
    @HAPreviews
    @Preview(name = "phone_dark", device = "spec:width=411dp,height=923dp", uiMode = UI_MODE_NIGHT_YES)
    @Composable
    fun `Backup home without servers`() {
        HAThemeForPreview {
            BackupHomeContent(SettingsBackupUiState.Content(emptyList()), { _, _ -> }, {}, {}, {})
        }
    }

    @PreviewTest
    @HAPreviews
    @Preview(name = "phone_dark", device = "spec:width=411dp,height=923dp", uiMode = UI_MODE_NIGHT_YES)
    @Composable
    fun `Restore with multiple servers`() {
        val backup = SettingsBackupData(
            SETTINGS_BACKUP_FORMAT,
            SETTINGS_BACKUP_VERSION,
            "2026.10.2",
            Instant.parse("2026-10-07T12:00:00Z"),
            listOf(BackupServerData("home", "Home"), BackupServerData("cabin", "Cabin")),
            androidAutoFavorites = emptyList(),
        )
        HAThemeForPreview {
            BackupRestoreContent(
                SettingsBackupUiState.Content(
                    listOf(BackupDestination(42, "Home server"), BackupDestination(99, "Cabin server")),
                    restore = RestoreDraft(backup, mapOf("home" to BackupServerTarget.Server(42))),
                    sectionRows = sectionSelections(BackupSections(), backup),
                ),
                { _, _ -> },
                { _, _ -> },
                {},
                {},
                {},
                {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Preview(name = "phone_dark", device = "spec:width=411dp,height=923dp", uiMode = UI_MODE_NIGHT_YES)
    @Composable
    fun `Confirm two Android Auto favorites`() {
        val backup = SettingsBackupData(
            SETTINGS_BACKUP_FORMAT,
            SETTINGS_BACKUP_VERSION,
            "2026.10.2",
            Instant.parse("2026-10-07T12:00:00Z"),
            listOf(BackupServerData("home", "Home")),
            androidAutoFavorites = listOf(
                BackupEntityReference("home", "cover.garage"),
                BackupEntityReference("home", "light.driveway"),
            ),
        )
        val servers = listOf(BackupDestination(42, "Home"))
        val plan = prepareSettingsRestore(backup, mapOf("home" to 42), servers, emptyMap())
        HAThemeForPreview {
            BackupRestoreContent(
                SettingsBackupUiState.Content(
                    servers,
                    restore = RestoreDraft(
                        backup,
                        mapOf(
                            "home" to BackupServerTarget.Server(42),
                        ),
                        plan,
                    ),
                ),
                { _, _ -> },
                { _, _ -> },
                {},
                {},
                {},
                {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Preview(name = "phone_dark", device = "spec:width=411dp,height=923dp", uiMode = UI_MODE_NIGHT_YES)
    @Composable
    fun `Confirm all settings categories`() {
        val backup = SettingsBackupData(
            SETTINGS_BACKUP_FORMAT,
            SETTINGS_BACKUP_VERSION,
            "2026.10.2",
            Instant.parse("2026-10-07T12:00:00Z"),
            listOf(BackupServerData("home", "Home")),
            androidAutoFavorites = listOf(
                BackupEntityReference("home", "cover.garage"),
                BackupEntityReference("home", "light.driveway"),
            ),
        )
        val plan = SettingsRestorePlan(
            changes = BackupSettingsChanges(
                serverIds = setOf(42),
                sensors = listOf(
                    RestoredSensorSelection("battery_level", 42, true),
                    RestoredSensorSelection("geocoded_location", 42, false),
                ),
                options = listOf(
                    SensorSetting("geocoded_location", "minimum_accuracy", "200", SensorSettingType.NUMBER),
                ),
                connections = mapOf(42 to WebsocketSetting.ALWAYS),
                frequency = SensorUpdateFrequencySetting.FAST_WHILE_CHARGING,
            ),
            favorites = listOf(AutoFavorite(42, "cover.garage"), AutoFavorite(42, "light.driveway")),
            issues = emptyList(),
        )
        HAThemeForPreview {
            BackupRestoreContent(
                SettingsBackupUiState.Content(
                    listOf(BackupDestination(42, "Home")),
                    restore = RestoreDraft(backup, mapOf("home" to BackupServerTarget.Server(42)), plan),
                ),
                { _, _ -> },
                { _, _ -> },
                {},
                {},
                {},
                {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Preview(name = "phone_dark", device = "spec:width=411dp,height=923dp", uiMode = UI_MODE_NIGHT_YES)
    @Composable
    fun `Choose a destination for a single server`() {
        RestoreServerPreview(listOf(BackupServerData("home", "Home")), emptyMap())
    }

    @PreviewTest
    @HAPreviews
    @Preview(name = "phone_dark", device = "spec:width=411dp,height=923dp", uiMode = UI_MODE_NIGHT_YES)
    @Composable
    fun `Restore with a server excluded`() {
        RestoreServerPreview(
            listOf(BackupServerData("home", "Home"), BackupServerData("cabin", "Cabin")),
            mapOf("home" to BackupServerTarget.Server(42), "cabin" to BackupServerTarget.Skip),
        )
    }

    @Composable
    private fun RestoreServerPreview(servers: List<BackupServerData>, targets: Map<String, BackupServerTarget>) {
        val backup = SettingsBackupData(
            SETTINGS_BACKUP_FORMAT,
            SETTINGS_BACKUP_VERSION,
            "2026.10.2",
            Instant.parse("2026-10-07T12:00:00Z"),
            servers,
            androidAutoFavorites = emptyList(),
        )
        HAThemeForPreview {
            BackupRestoreContent(
                SettingsBackupUiState.Content(
                    listOf(BackupDestination(42, "Home server"), BackupDestination(99, "Cabin server")),
                    restore = RestoreDraft(backup, targets),
                    sectionRows = sectionSelections(BackupSections(), backup),
                ),
                { _, _ -> },
                { _, _ -> },
                {},
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
