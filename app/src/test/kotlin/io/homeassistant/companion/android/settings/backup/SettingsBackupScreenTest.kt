package io.homeassistant.companion.android.settings.backup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.common.data.backup.BackupSections
import io.homeassistant.companion.android.common.data.backup.BackupServerData
import io.homeassistant.companion.android.testing.unit.stringResource
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class SettingsBackupScreenTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<HiltComponentActivity>()

    @Test
    fun `Given no selected sections when viewing home then export is disabled and selecting calls back`() {
        var selected: BackupSection? = null
        val sections = BackupSections(false, false, false, false, false)
        compose.setContent {
            HATheme {
                BackupHomeContent(
                    SettingsBackupUiState.Content(emptyList(), sections, sectionSelections(sections)),
                    onSelect = { section, _ -> selected = section },
                    onExport = {},
                    onImport = {},
                    onManageSensors = {},
                )
            }
        }
        compose.onNodeWithText(compose.stringResource(R.string.backup_export)).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText(compose.stringResource(R.string.backup_choose_export)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(compose.stringResource(R.string.backup_section_favorites)).assertIsOff()
        compose.onNodeWithText(compose.stringResource(R.string.backup_section_favorites)).performScrollTo().performClick()
        assertEquals(BackupSection.AndroidAutoFavorites, selected)
    }

    @Test
    fun `Given a restore draft when mapping and reviewing then callbacks fire and applying requires a preview`() {
        var mapping: Pair<String, BackupServerTarget>? = null
        var reviewed = false
        var state by mutableStateOf(SettingsBackupUiState.Content(listOf(BackupDestination(42, "Destination")), restore = RestoreDraft(backupFixture())))
        compose.setContent {
            HATheme {
                BackupRestoreContent(state, { _, _ -> }, { reference, target ->
                    mapping = reference to target
                    state = state.copy(restore = state.restore?.copy(targets = mapOf(reference to target)))
                }, { reviewed = true }, {}, {}, {})
            }
        }
        compose.onNode(hasText(compose.stringResource(R.string.backup_review)) and hasClickAction()).performScrollTo().assertIsNotEnabled()
        compose.onNode(hasText(compose.activity.getString(R.string.backup_restore_server, "Home")) and isToggleable()).assertDoesNotExist()
        compose.onNodeWithText("Destination").performScrollTo().performClick()
        compose.onNodeWithText("Destination").assertIsSelected()
        assertEquals("home" to BackupServerTarget.Server(42), mapping)
        compose.onNode(hasText(compose.stringResource(R.string.backup_review)) and hasClickAction()).performScrollTo().performClick()
        assertTrue(reviewed)
        compose.onNodeWithText(compose.stringResource(R.string.backup_apply)).assertDoesNotExist()
    }

    @Test
    fun `Given multiple saved servers when excluding and including one then destinations and validation follow the server switches`() {
        val backup = backupFixture().copy(servers = listOf(BackupServerData("home", "Home"), BackupServerData("cabin", "Cabin")), sensorOptions = null)
        var state by mutableStateOf(
            SettingsBackupUiState.Content(
                listOf(BackupDestination(42, "Destination")),
                restore = RestoreDraft(backup),
                sectionRows = sectionSelections(BackupSections(), backup),
            ),
        )
        compose.setContent {
            HATheme {
                BackupRestoreContent(state, { _, _ -> }, { reference, target ->
                    state = state.copy(restore = state.restore?.let { it.copy(targets = it.targets + (reference to target)) })
                }, {}, {}, {}, {})
            }
        }
        compose.onAllNodesWithText("Destination")[0].performScrollTo().performClick()
        val cabin = compose.onNodeWithText(compose.activity.getString(R.string.backup_restore_server, "Cabin"))
        cabin.performScrollTo().assertIsOn().performClick()
        cabin.assertIsOff()
        compose.onNodeWithText(compose.stringResource(R.string.backup_server_excluded)).assertIsDisplayed()
        compose.onAllNodesWithText("Destination").fetchSemanticsNodes().let { assertEquals(1, it.size) }
        compose.onNodeWithText(compose.stringResource(R.string.backup_review)).performScrollTo().assertIsEnabled()
        cabin.performScrollTo().performClick()
        compose.onNodeWithText(compose.stringResource(R.string.backup_review)).performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { assertEquals(BackupServerTarget.Unselected, state.restore?.targets?.get("cabin")) }
    }

    @Test
    fun `Given restore navigation when cancelling and going back then home is restored without applying`() {
        lateinit var navigation: NavHostController
        var cancelled = 0
        var applied = 0
        var state by mutableStateOf(SettingsBackupUiState.Content(emptyList(), restore = RestoreDraft(backupFixture())))
        compose.setContent {
            navigation = rememberNavController()
            HATheme {
                BackupNavigation(
                    state, navigation, { _, _ -> }, {},
                    onImport = { navigation.navigate(BackupRestoreRoute) },
                    onMapServer = { _, _ -> }, onReview = {}, onRestore = { applied++ },
                    onCancel = {
                        cancelled++
                        state = state.copy(restore = null)
                    },
                    onEdit = {}, onRetry = {}, onManageSensors = {},
                )
            }
        }
        compose.onNodeWithText(compose.stringResource(R.string.backup_import)).performScrollTo().performClick()
        compose.runOnIdle { assertTrue(navigation.currentDestination!!.hasRoute<BackupRestoreRoute>()) }
        compose.onNodeWithText(compose.stringResource(R.string.cancel)).performScrollTo().performClick()
        compose.runOnIdle {
            assertTrue(navigation.currentDestination!!.hasRoute<BackupHomeRoute>())
            state = state.copy(restore = RestoreDraft(backupFixture()))
        }
        compose.onNodeWithText(compose.stringResource(R.string.backup_import)).performScrollTo().performClick()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText(compose.stringResource(R.string.backup_import)).performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(navigation.currentDestination!!.hasRoute<BackupHomeRoute>())
            assertEquals(2, cancelled)
            assertEquals(0, applied)
        }
    }

    @Test
    fun `Given confirmation when shown then Android Auto has its own count and back returns to selection without applying`() {
        val backup = backupFixture().copy(sensorOptions = null, servers = backupFixture().servers.map { it.copy(sensors = null, persistentConnection = null) })
        val destinations = listOf(BackupDestination(42, "Destination"))
        val plan = prepareSettingsRestore(backup, mapOf("home" to 42), destinations, emptyMap())
        var applied = 0
        lateinit var navigation: NavHostController
        var state by mutableStateOf(SettingsBackupUiState.Content(destinations, restore = RestoreDraft(backup, mapOf("home" to BackupServerTarget.Server(42)), plan)))
        compose.setContent {
            navigation = rememberNavController()
            HATheme {
                BackupNavigation(
                    state, navigation, { _, _ -> }, {},
                    onImport = { navigation.navigate(BackupRestoreRoute) },
                    onMapServer = { _, _ -> }, onReview = {}, onRestore = { applied++ }, onCancel = {},
                    onEdit = { state = state.copy(restore = state.restore?.copy(plan = null)) },
                    onRetry = {}, onManageSensors = {},
                )
            }
        }
        compose.onNodeWithText(compose.stringResource(R.string.backup_import)).performScrollTo().performClick()
        compose.onNode(hasText(compose.stringResource(R.string.backup_section_favorites)) and hasText("2")).assertIsDisplayed()
        compose.onNodeWithText(compose.stringResource(R.string.backup_section_sensor_options)).assertDoesNotExist()
        compose.onNodeWithText(compose.stringResource(R.string.backup_favorites_description)).assertDoesNotExist()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText(compose.stringResource(R.string.backup_choose_settings)).assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(navigation.currentDestination!!.hasRoute<BackupRestoreRoute>())
            assertEquals(mapOf("home" to 42), state.restoreServers?.mapping)
            assertEquals(0, applied)
        }
    }
}
