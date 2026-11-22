package io.homeassistant.companion.android.common.compose.composable

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
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
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `Given empty field when rendered then search label is shown and clear icon is hidden`() {
        composeTestRule.apply {
            subject(SearchFieldState())

            waitForIdle()

            onNodeWithText(stringResource(commonR.string.search))
                .assertIsDisplayed()

            onNodeWithContentDescription(stringResource(commonR.string.clear_search))
                .assertDoesNotExist()
        }
    }

    @Test
    fun `Given empty field when text entered then clear icon becomes visible`() {
        composeTestRule.apply {
            subject(SearchFieldState())

            onNodeWithText(stringResource(commonR.string.search))
                .performTextInput("chrome")

            waitForIdle()

            onNodeWithContentDescription(stringResource(commonR.string.clear_search))
                .assertIsDisplayed()
        }
    }

    @Test
    fun `Given text in field when clear icon clicked then field clears and query resets immediately`() {
        composeTestRule.apply {
            val state = SearchFieldState()
            subject(state)

            onNodeWithText(stringResource(commonR.string.search))
                .performTextInput("chrome")

            advancePastDebounce()
            assertEquals("chrome", state.query)

            onNodeWithContentDescription(stringResource(commonR.string.clear_search))
                .performClick()
            waitForIdle()

            // Clearing resets the query without waiting for the debounce
            assertEquals("", state.query)

            // The clear icon only shows for non-empty text, so it disappears once the field is cleared

            onNodeWithContentDescription(stringResource(commonR.string.clear_search))
                .assertDoesNotExist()

            onNodeWithText(stringResource(commonR.string.search))
                .assertIsDisplayed()
        }
    }

    @Test
    fun `Given non-empty text when typed then query updates only after the debounce elapses`() {
        composeTestRule.apply {
            val state = SearchFieldState()
            subject(state)

            waitForIdle()
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            // Freeze compose auto-advancing so we can control time precisely
            mainClock.autoAdvance = false

            onNodeWithText(stringResource(commonR.string.search))
                .performTextInput("chrome")

            // Let recomposition process the new text (frames only, no coroutine time)
            mainClock.advanceTimeByFrame()

            // Advance to just before the debounce boundary, only via the scheduler
            mainDispatcherRule.testDispatcher.scheduler.advanceTimeBy(SEARCH_FIELD_DEFAULT_DEBOUNCE.inWholeMilliseconds - 50)
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            assertEquals("Expected the query to stay empty before the debounce", "", state.query)

            // Cross the debounce boundary: the typed value is now propagated
            mainClock.autoAdvance = true
            advancePastDebounce()

            assertEquals("chrome", state.query)
        }
    }

    @Test
    fun `Given external clear when state cleared then field empties without user interaction`() {
        composeTestRule.apply {
            val state = SearchFieldState()
            subject(state)

            onNodeWithText(stringResource(commonR.string.search))
                .performTextInput("chrome")

            advancePastDebounce()

            state.clear()
            waitForIdle()

            assertEquals("", state.rawText)
            assertEquals("", state.query)
            onNodeWithContentDescription(stringResource(commonR.string.clear_search))
                .assertDoesNotExist()
        }
    }

    private fun AndroidComposeTestRule<*, *>.subject(state: SearchFieldState) {
        setContent {
            HAThemeForPreview {
                HASearchField(state = state)
            }
        }
    }

    private fun AndroidComposeTestRule<*, *>.advancePastDebounce() {
        mainClock.advanceTimeBy(SEARCH_FIELD_DEFAULT_DEBOUNCE.inWholeMilliseconds + 50)
        waitForIdle()
    }
}
