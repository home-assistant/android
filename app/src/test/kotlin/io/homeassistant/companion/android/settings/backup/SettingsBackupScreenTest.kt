package io.homeassistant.companion.android.settings.backup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
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
                }, { reviewed = true }, {}, {})
            }
        }
        compose.onNode(hasText(compose.stringResource(R.string.backup_review)) and hasClickAction()).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText(compose.stringResource(R.string.backup_choose_server)).performScrollTo().performClick()
        compose.onNodeWithText("Destination").performClick()
        assertEquals("home" to BackupServerTarget.Server(42), mapping)
        compose.onNode(hasText(compose.stringResource(R.string.backup_review)) and hasClickAction()).performScrollTo().performClick()
        assertTrue(reviewed)
        compose.onNodeWithText(compose.stringResource(R.string.backup_apply)).assertDoesNotExist()
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
                    onRetry = {}, onManageSensors = {},
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
}
