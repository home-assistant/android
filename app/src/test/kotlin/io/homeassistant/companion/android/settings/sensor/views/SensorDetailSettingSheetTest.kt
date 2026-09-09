package io.homeassistant.companion.android.settings.sensor.views

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.database.sensor.SensorSetting
import io.homeassistant.companion.android.database.sensor.SensorSettingType
import io.homeassistant.companion.android.settings.sensor.SensorDetailViewModel.Companion.SettingDialogState
import io.homeassistant.companion.android.testing.unit.stringResource
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val CHROME_LABEL = "Chrome"
private const val FIREFOX_LABEL = "Firefox"
private const val CHROME_PACKAGE = "com.google.chrome"
private const val FILTER_TIMEOUT_MILLIS = 2_000L

private const val EXAMPLE_ID = "com.example.app"
private const val SHEET_TITLE = "Allow list"

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class SensorDetailSettingSheetTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    private val entries = listOf(
        SettingEntry(id = CHROME_PACKAGE, label = "Chrome\n(com.google.chrome)"),
        SettingEntry(id = "org.mozilla.firefox", label = "Firefox\n(org.mozilla.firefox)"),
        SettingEntry(id = EXAMPLE_ID, label = "Example App\n(com.example.app)"),
    )

    @Test
    fun `Given empty query when filtering then return all entries`() {
        val result = filterSettingEntries(entries, query = "")

        assertEquals(entries, result)
    }

    @Test
    fun `Given blank query when filtering then return all entries`() {
        val result = filterSettingEntries(entries, query = "   ")

        assertEquals(entries, result)
    }

    @Test
    fun `Given query matching app name when filtering then return matching entries`() {
        val result = filterSettingEntries(entries, query = "Chrome")

        assertEquals(listOf(entries[0]), result)
    }

    @Test
    fun `Given query matching package name in label when filtering then return matching entries`() {
        val result = filterSettingEntries(entries, query = "com.google")

        assertEquals(listOf(entries[0]), result)
    }

    @Test
    fun `Given case-insensitive query when filtering then return matches`() {
        val result = filterSettingEntries(entries, query = "CHROME")

        assertEquals(listOf(entries[0]), result)
    }

    @Test
    fun `Given query matching no entries when filtering then return empty list`() {
        val result = filterSettingEntries(entries, query = "nonexistent")

        assertEquals(emptyList<SettingEntry>(), result)
    }

    @Test
    fun `Given query with leading and trailing spaces when filtering then trim and match`() {
        val result = filterSettingEntries(entries, query = " Chrome ")

        assertEquals(listOf(entries[0]), result)
    }

    @Test
    fun `Given a loading sheet when the selection arrives then saving keeps the previous selection`() {
        composeTestRule.apply {
            testSheet(dialogState(isLoading = true, entries = emptyList(), selected = emptyList())) {
                // The view model always opens the sheet loading, then replaces the state once the
                // entries and the stored selection are resolved.
                state = dialogState(isLoading = false, entries = entries, selected = listOf(EXAMPLE_ID))
                waitForIdle()

                onNodeWithText(stringResource(commonR.string.save)).performClick()
                waitForIdle()

                assertEquals(listOf(EXAMPLE_ID), saved?.entriesSelected)
            }
        }
    }

    @Test
    fun `Given a loading sheet when the entries arrive then the no results placeholder is never shown`() {
        composeTestRule.apply {
            mainClock.autoAdvance = false
            testSheet(dialogState(isLoading = true, entries = emptyList(), selected = emptyList())) {
                mainClock.advanceTimeByFrame()

                state = dialogState(isLoading = false, entries = entries, selected = emptyList())
                mainClock.advanceTimeByFrame()

                // With no query typed there is nothing to filter, so the entries must render on the
                // very frame they arrive rather than after a round trip through the filtering coroutine.
                onNodeWithText(stringResource(commonR.string.sensor_setting_allow_list_no_results))
                    .assertDoesNotExist()
            }
        }
    }

    @Test
    fun `Given an unselected entry when it is tapped then saving includes it`() {
        composeTestRule.apply {
            testSheet(dialogState(isLoading = false, entries = entries, selected = emptyList())) {
                onNodeWithText(CHROME_LABEL).performClick()
                onNodeWithText(stringResource(commonR.string.save)).performClick()
                waitForIdle()

                assertEquals(listOf(CHROME_PACKAGE), saved?.entriesSelected)
            }
        }
    }

    @Test
    fun `Given an unselected entry when its checkbox is tapped then saving includes it`() {
        composeTestRule.apply {
            testSheet(dialogState(isLoading = false, entries = entries, selected = emptyList())) {
                // Tap the checkbox itself rather than the label. It is the obvious target, so it
                // must toggle the entry rather than swallow the tap.
                onNodeWithTag(settingEntryCheckboxTag(CHROME_PACKAGE), useUnmergedTree = true)
                    .performClick()
                onNodeWithText(stringResource(commonR.string.save)).performClick()
                waitForIdle()

                assertEquals(listOf(CHROME_PACKAGE), saved?.entriesSelected)
            }
        }
    }

    @Test
    fun `Given a toggled entry when cancelling then nothing is saved`() {
        composeTestRule.apply {
            testSheet(dialogState(isLoading = false, entries = entries, selected = emptyList())) {
                onNodeWithText(CHROME_LABEL).performClick()
                onNodeWithText(stringResource(commonR.string.cancel)).performClick()
                waitForIdle()

                assertTrue(dismissed)
                assertNull(saved)
            }
        }
    }

    @Test
    fun `Given a search query when typed then only matching entries stay visible`() {
        composeTestRule.apply {
            // The search field only appears above the entry count threshold.
            testSheet(dialogState(isLoading = false, entries = manyEntries(), selected = emptyList())) {
                onNodeWithText(stringResource(commonR.string.search)).performTextInput("google")

                // The search field debounces the query, so wait for the filtering rather than a frame.
                waitUntil(timeoutMillis = FILTER_TIMEOUT_MILLIS) {
                    onAllNodesWithText(FIREFOX_LABEL).fetchSemanticsNodes().isEmpty()
                }
                onNodeWithText(CHROME_PACKAGE).assertIsDisplayed()
            }
        }
    }

    private class TestHelper(initialState: SettingDialogState) {
        var state by mutableStateOf(initialState)
        var saved: SettingDialogState? = null
        var dismissed = false
    }

    private fun AndroidComposeTestRule<*, *>.testSheet(
        initialState: SettingDialogState,
        block: TestHelper.() -> Unit,
    ) {
        TestHelper(initialState).apply {
            setContent {
                HATheme {
                    SensorDetailSettingSheet(
                        title = SHEET_TITLE,
                        state = state,
                        onDismiss = { dismissed = true },
                        onSave = { saved = it },
                    )
                }
            }
            block()
        }
    }

    /** Enough entries for [SettingDialogState.showSearch] to render the search field. */
    private fun manyEntries() = entries + (0..10).map { SettingEntry(id = "id.$it", label = "Filler $it") }

    private fun dialogState(isLoading: Boolean, entries: List<SettingEntry>, selected: List<String>) = SettingDialogState(
        setting = SensorSetting(
            sensorId = "last_notification",
            name = "allow_list",
            value = selected.joinToString(),
            valueType = SensorSettingType.LIST_APPS,
        ),
        isLoading = isLoading,
        entries = entries,
        entriesSelected = selected,
    )
}
