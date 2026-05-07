package io.homeassistant.companion.android.nfc.views

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.nfc.TagReaderUiState
import io.homeassistant.companion.android.testing.unit.ConsoleLogRule
import io.homeassistant.companion.android.testing.unit.stringResource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class TagReaderScreenTest {

    @get:Rule(order = 0)
    var consoleLog = ConsoleLogRule()

    @get:Rule(order = 1)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Test
    fun `Given ApprovingTag state then approval content with Allow once and Allow always buttons is displayed`() {
        composeTestRule.apply {
            setContent {
                TagReaderScreen(
                    state = TagReaderUiState.ApprovingTag("custom-tag-foo"),
                    onAllowOnce = {},
                    onAllowAlways = {},
                    onDismissed = {},
                    onErrorAcknowledged = {},
                    onFinished = {},
                )
            }

            onNodeWithText(stringResource(commonR.string.tag_approval_title)).assertExists()
            onNodeWithText(
                activity.getString(commonR.string.tag_approval_description, "custom-tag-foo"),
            ).assertExists()
            onNodeWithText(stringResource(commonR.string.tag_allow_once)).assertExists()
            onNodeWithText(stringResource(commonR.string.tag_allow_always)).assertExists()
            onNodeWithContentDescription(stringResource(commonR.string.cancel)).assertExists()
        }
    }

    @Test
    fun `Given ApprovingTag state when Allow once is clicked then onAllowOnce is invoked`() {
        var allowOnceCalled = false
        var allowAlwaysCalled = false

        composeTestRule.apply {
            setContent {
                TagReaderScreen(
                    state = TagReaderUiState.ApprovingTag("custom-tag-foo"),
                    onAllowOnce = { allowOnceCalled = true },
                    onAllowAlways = { allowAlwaysCalled = true },
                    onDismissed = {},
                    onErrorAcknowledged = {},
                    onFinished = {},
                )
            }

            onNodeWithText(stringResource(commonR.string.tag_allow_once)).performClick()

            assertTrue("onAllowOnce should be invoked", allowOnceCalled)
            assertFalse("onAllowAlways should not be invoked", allowAlwaysCalled)
        }
    }

    @Test
    fun `Given ApprovingTag state when Allow always is clicked then onAllowAlways is invoked`() {
        var allowOnceCalled = false
        var allowAlwaysCalled = false

        composeTestRule.apply {
            setContent {
                TagReaderScreen(
                    state = TagReaderUiState.ApprovingTag("custom-tag-foo"),
                    onAllowOnce = { allowOnceCalled = true },
                    onAllowAlways = { allowAlwaysCalled = true },
                    onDismissed = {},
                    onErrorAcknowledged = {},
                    onFinished = {},
                )
            }

            onNodeWithText(stringResource(commonR.string.tag_allow_always)).performClick()

            assertTrue("onAllowAlways should be invoked", allowAlwaysCalled)
            assertFalse("onAllowOnce should not be invoked", allowOnceCalled)
        }
    }

    @Test
    fun `Given ApprovingTag state when close button is clicked then onDismissed is invoked`() {
        var dismissedCalled = false

        composeTestRule.apply {
            setContent {
                TagReaderScreen(
                    state = TagReaderUiState.ApprovingTag("custom-tag-foo"),
                    onAllowOnce = {},
                    onAllowAlways = {},
                    onDismissed = { dismissedCalled = true },
                    onErrorAcknowledged = {},
                    onFinished = {},
                )
            }

            onNodeWithContentDescription(stringResource(commonR.string.cancel)).performClick()

            assertTrue("onDismissed should be invoked", dismissedCalled)
        }
    }

    @Test
    fun `Given Scanning state then loading content is displayed and approval buttons are not`() {
        composeTestRule.apply {
            setContent {
                TagReaderScreen(
                    state = TagReaderUiState.Scanning,
                    onAllowOnce = {},
                    onAllowAlways = {},
                    onDismissed = {},
                    onErrorAcknowledged = {},
                    onFinished = {},
                )
            }

            onNodeWithText(stringResource(commonR.string.tag_reader_title)).assertExists()
            onNodeWithText(stringResource(commonR.string.tag_allow_once)).assertDoesNotExist()
            onNodeWithText(stringResource(commonR.string.tag_allow_always)).assertDoesNotExist()
        }
    }

    @Test
    fun `Given Initial state then nothing from the sheet is displayed`() {
        composeTestRule.apply {
            setContent {
                TagReaderScreen(
                    state = TagReaderUiState.Initial,
                    onAllowOnce = {},
                    onAllowAlways = {},
                    onDismissed = {},
                    onErrorAcknowledged = {},
                    onFinished = {},
                )
            }

            onNodeWithText(stringResource(commonR.string.tag_approval_title)).assertDoesNotExist()
            onNodeWithText(stringResource(commonR.string.tag_allow_once)).assertDoesNotExist()
            onNodeWithText(stringResource(commonR.string.tag_allow_always)).assertDoesNotExist()
            onNodeWithContentDescription(stringResource(commonR.string.cancel)).assertDoesNotExist()
        }
    }

    @Test
    fun `Given Done state with no prior sheet then onFinished is invoked immediately`() {
        var finishedCalled = false

        composeTestRule.apply {
            setContent {
                TagReaderScreen(
                    state = TagReaderUiState.Done,
                    onAllowOnce = {},
                    onAllowAlways = {},
                    onDismissed = {},
                    onErrorAcknowledged = {},
                    onFinished = { finishedCalled = true },
                )
            }

            mainClock.advanceTimeUntil(timeoutMillis = 5_000) { finishedCalled }
            assertTrue("onFinished should be invoked", finishedCalled)
        }
    }

    @Test
    fun `Given Error state then onErrorAcknowledged is invoked after the snackbar is shown`() {
        var acknowledgedCalled = false

        composeTestRule.apply {
            setContent {
                TagReaderScreen(
                    state = TagReaderUiState.Error(commonR.string.nfc_processing_tag_error),
                    onAllowOnce = {},
                    onAllowAlways = {},
                    onDismissed = {},
                    onErrorAcknowledged = { acknowledgedCalled = true },
                    onFinished = {},
                )
            }

            // Advance the test clock past the snackbar's auto-dismiss timeout instead of
            // wall-clock polling. The snackbar is shown by a LaunchedEffect that calls
            // showSnackbar() which suspends until the snackbar is dismissed and then invokes
            // onErrorAcknowledged.
            mainClock.advanceTimeUntil(
                4000L, // This is SnackbarDuration.Short.toMillis but it is not public
            ) { acknowledgedCalled }

            assertTrue("onErrorAcknowledged should be invoked once the snackbar is dismissed", acknowledgedCalled)
        }
    }
}
