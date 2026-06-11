package io.homeassistant.companion.android.common.compose.composable

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit4Rule
import io.homeassistant.companion.android.testing.unit.stringResource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class HASearchFieldTest {

    @get:Rule(order = 0)
    val mainDispatcherRule = MainDispatcherJUnit4Rule()

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<ComponentActivity>(mainDispatcherRule.testDispatcher)

    private fun subject(
        query: String = "",
        onQueryChange: (String) -> Unit = {},
    ) {
        composeTestRule.setContent {
            HAThemeForPreview {
                HASearchField(
                    query = query,
                    onQueryChange = onQueryChange,
                )
            }
        }
    }

    private fun advancePastDebounce() {
        mainDispatcherRule.testDispatcher.scheduler.advanceTimeBy(SEARCH_FIELD_DEFAULT_DEBOUNCE.inWholeMilliseconds + 50)
        mainDispatcherRule.testDispatcher.scheduler.runCurrent()
        composeTestRule.mainClock.advanceTimeBy(SEARCH_FIELD_DEFAULT_DEBOUNCE.inWholeMilliseconds + 50)
        composeTestRule.waitForIdle()
    }

    @Test
    fun `Given empty field when rendered then search label is shown and clear icon is hidden`() {
        subject(query = "")

        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithText(composeTestRule.stringResource(commonR.string.search))
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithContentDescription(composeTestRule.stringResource(commonR.string.clear_search))
            .assertDoesNotExist()
    }

    @Test
    fun `Given empty field when text entered then clear icon becomes visible`() {
        subject()

        composeTestRule.onNodeWithText(composeTestRule.stringResource(commonR.string.search))
            .performTextInput("chrome")

        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithContentDescription(composeTestRule.stringResource(commonR.string.clear_search))
            .assertIsDisplayed()
    }

    @Test
    fun `Given text in field when clear icon clicked then field clears, search label reappears and empty string emitted`() {
        val emitted = mutableListOf<String>()

        subject(onQueryChange = { emitted += it })

        composeTestRule.onNodeWithText(composeTestRule.stringResource(commonR.string.search))
            .performTextInput("chrome")

        advancePastDebounce()

        composeTestRule
            .onNodeWithContentDescription(composeTestRule.stringResource(commonR.string.clear_search))
            .performClick()

        advancePastDebounce()

        // The clear icon only shows for non-empty text, so it disappears once the field is cleared.
        composeTestRule
            .onNodeWithContentDescription(composeTestRule.stringResource(commonR.string.clear_search))
            .assertDoesNotExist()

        composeTestRule
            .onNodeWithText(composeTestRule.stringResource(commonR.string.search))
            .assertIsDisplayed()

        assertTrue(
            "Expected 'chrome' emitted before clear, got: $emitted",
            emitted.contains("chrome"),
        )
        assertEquals(
            "Expected empty string emitted after clear, got: $emitted",
            "",
            emitted.last(),
        )
    }

    @Test
    fun `Given non-empty text when typed then onQueryChange emits only after the debounce elapses`() {
        val emitted = mutableListOf<String>()

        subject(onQueryChange = { emitted += it })

        // Flush the initial empty-string emit that fires on first composition.
        composeTestRule.waitForIdle()
        mainDispatcherRule.testDispatcher.scheduler.runCurrent()

        // Freeze compose auto-advancing so we can control time precisely.
        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.onNodeWithText(composeTestRule.stringResource(commonR.string.search))
            .performTextInput("chrome")

        // Let recomposition process the new text (frames only, no coroutine time).
        composeTestRule.mainClock.advanceTimeByFrame()

        // Advance to just before the debounce boundary — only via the scheduler.
        mainDispatcherRule.testDispatcher.scheduler.advanceTimeBy(SEARCH_FIELD_DEFAULT_DEBOUNCE.inWholeMilliseconds - 50)
        mainDispatcherRule.testDispatcher.scheduler.runCurrent()

        assertEquals(
            "Expected only the initial empty emit before debounce, got: $emitted",
            listOf(""),
            emitted,
        )

        // Cross the debounce boundary: the typed value is now propagated.
        composeTestRule.mainClock.autoAdvance = true
        advancePastDebounce()

        assertTrue(
            "Expected 'chrome' to be emitted after debounce, got: $emitted",
            emitted.contains("chrome"),
        )
    }
}
