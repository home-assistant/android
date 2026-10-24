package io.homeassistant.companion.android.settings.sensor.views

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
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
    fun `Given a query matching the app name when searching then only that entry is listed`() {
        composeTestRule.assertOnlyChromeIsListed(query = "Chrome")
    }

    @Test
    fun `Given a query matching the package name when searching then only that entry is listed`() {
        composeTestRule.assertOnlyChromeIsListed(query = "com.google")
    }

    @Test
    fun `Given a query in another case when searching then it still matches`() {
        composeTestRule.assertOnlyChromeIsListed(query = "CHROME")
    }

    @Test
    fun `Given a query with surrounding spaces when searching then they are trimmed`() {
        composeTestRule.assertOnlyChromeIsListed(query = " Chrome ")
    }

    @Test
    fun `Given a query matching no entry when searching then the no results placeholder is shown`() {
        composeTestRule.apply {
            testSheet(dialogState(isLoading = false, entries = manyEntries(), selected = emptyList())) {
                search("nonexistent")

                waitUntil(timeoutMillis = FILTER_TIMEOUT_MILLIS) {
                    onAllNodesWithText(stringResource(commonR.string.sensor_setting_allow_list_no_results))
                        .fetchSemanticsNodes().isNotEmpty()
                }
            }
        }
    }

    @Test
    fun `Given a filtered list when the query is blanked then every entry is listed again`() {
        composeTestRule.apply {
            testSheet(dialogState(isLoading = false, entries = manyEntries(), selected = emptyList())) {
                search("Chrome")
                waitUntil(timeoutMillis = FILTER_TIMEOUT_MILLIS) {
                    onAllNodesWithText(FIREFOX_LABEL).fetchSemanticsNodes().isEmpty()
                }

                // Whitespace is trimmed away to nothing, so it must restore the full list.
                onNodeWithText(stringResource(commonR.string.search)).performTextReplacement("   ")

                waitUntil(timeoutMillis = FILTER_TIMEOUT_MILLIS) {
                    onAllNodesWithText(FIREFOX_LABEL).fetchSemanticsNodes().isNotEmpty()
                }
            }
        }
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

    private class TestHelper(initialState: SettingDialogState) {
        var state by mutableStateOf(initialState)
        var saved: SettingDialogState? = null
        var dismissed = false
    }

    /** Types [query] into the search field of an open sheet. */
    private fun AndroidComposeTestRule<*, *>.search(query: String) {
        onNodeWithText(stringResource(commonR.string.search)).performTextInput(query)
    }

    /** Opens a sheet with a searchable list, searches [query] and expects Chrome as the only match. */
    private fun AndroidComposeTestRule<*, *>.assertOnlyChromeIsListed(query: String) {
        testSheet(dialogState(isLoading = false, entries = manyEntries(), selected = emptyList())) {
            search(query)

            // The search field debounces the query, so wait for the filtering rather than a frame.
            waitUntil(timeoutMillis = FILTER_TIMEOUT_MILLIS) {
                onAllNodesWithText(FIREFOX_LABEL).fetchSemanticsNodes().isEmpty()
            }
            // Matched against the package on the entry's second line, so the assertion cannot also
            // match the text sitting in the search field itself.
            onNodeWithText(CHROME_PACKAGE).assertIsDisplayed()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
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
