package io.homeassistant.companion.android.notifications.alarm

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.BellRing
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.testing.unit.stringResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class FullScreenAlarmScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    private val actions = listOf(
        FullScreenAlarmAction(index = 0, title = "Silence siren"),
        FullScreenAlarmAction(index = 1, title = "Call security"),
        FullScreenAlarmAction(index = 2, title = "Turn on lights"),
    )

    private fun uiState(
        canOpen: Boolean = true,
        isFullScreenIntentAllowed: Boolean = true,
        image: ImageBitmap? = null,
    ) = FullScreenAlarmUiState(
        icon = Mdi.BellRing,
        title = "Intrusion detected",
        message = "Motion in the living room",
        canOpen = canOpen,
        actions = actions,
        isFullScreenIntentAllowed = isFullScreenIntentAllowed,
        image = image,
    )

    @Test
    fun `Given three actions when one is clicked then onActionClick is invoked with that action`() {
        val clicked = mutableListOf<FullScreenAlarmAction>()
        composeTestRule.apply {
            setContent {
                FullScreenAlarmScreen(
                    uiState = uiState(),
                    onDismiss = {},
                    onOpen = {},
                    onActionClick = { clicked += it },
                    onAllowFullScreen = {},
                )
            }

            actions.forEach { onNodeWithText(it.title).performScrollTo().performClick() }
        }

        assertEquals(actions, clicked)
    }

    @Test
    fun `Given alarm when Dismiss and Open are clicked then their callbacks are invoked`() {
        var dismissed = false
        var opened = false
        composeTestRule.apply {
            setContent {
                FullScreenAlarmScreen(
                    uiState = uiState(),
                    onDismiss = { dismissed = true },
                    onOpen = { opened = true },
                    onActionClick = {},
                    onAllowFullScreen = {},
                )
            }

            onNodeWithText(stringResource(commonR.string.full_screen_alarm_open)).performScrollTo().performClick()
            onNodeWithText(stringResource(commonR.string.full_screen_alarm_dismiss)).performScrollTo().performClick()
        }

        assertTrue(opened)
        assertTrue(dismissed)
    }

    @Test
    fun `Given nothing to open and permission allowed then Open and the permission banner are hidden`() {
        composeTestRule.apply {
            setContent {
                FullScreenAlarmScreen(
                    uiState = uiState(canOpen = false),
                    onDismiss = {},
                    onOpen = {},
                    onActionClick = {},
                    onAllowFullScreen = {},
                )
            }

            onNodeWithText(stringResource(commonR.string.full_screen_alarm_open)).assertDoesNotExist()
            onNodeWithText(stringResource(commonR.string.full_screen_alarm_permission_missing)).assertDoesNotExist()
        }
    }

    @Test
    fun `Given missing full-screen permission when Allow is clicked then onAllowFullScreen is invoked`() {
        var allowClicked = false
        composeTestRule.apply {
            setContent {
                FullScreenAlarmScreen(
                    uiState = uiState(isFullScreenIntentAllowed = false),
                    onDismiss = {},
                    onOpen = {},
                    onActionClick = {},
                    onAllowFullScreen = { allowClicked = true },
                )
            }

            onNodeWithText(stringResource(commonR.string.allow)).performScrollTo().performClick()
        }

        assertTrue(allowClicked)
    }

    @Test
    fun `Given an image when showing the alarm then the image is displayed`() {
        composeTestRule.apply {
            setContent {
                FullScreenAlarmScreen(
                    uiState = uiState(image = ImageBitmap(width = 160, height = 90)),
                    onDismiss = {},
                    onOpen = {},
                    onActionClick = {},
                    onAllowFullScreen = {},
                )
            }

            onNodeWithTag(ALARM_IMAGE_TEST_TAG).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun `Given no image when showing the alarm then no image is displayed`() {
        composeTestRule.apply {
            setContent {
                FullScreenAlarmScreen(
                    uiState = uiState(),
                    onDismiss = {},
                    onOpen = {},
                    onActionClick = {},
                    onAllowFullScreen = {},
                )
            }

            onNodeWithTag(ALARM_IMAGE_TEST_TAG).assertDoesNotExist()
        }
    }
}
