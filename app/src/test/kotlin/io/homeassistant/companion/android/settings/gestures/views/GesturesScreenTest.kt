package io.homeassistant.companion.android.settings.gestures.views

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.util.GestureAction
import io.homeassistant.companion.android.common.util.GesturePointers
import io.homeassistant.companion.android.common.util.HAGesture
import io.homeassistant.companion.android.testing.unit.stringResource
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w600dp-h2000dp")
@HiltAndroidTest
class GesturesScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    private val availableGestures = mutableStateOf<List<HAGesture>>(HAGesture.entries)
    private val gestureActions = mutableStateMapOf<HAGesture, GestureAction>().apply {
        putAll(HAGesture.entries.associateWith { GestureAction.NONE })
        this[HAGesture.SWIPE_LEFT_ONE_EDGE] = GestureAction.QUICKBAR_DEFAULT
        this[HAGesture.SWIPE_RIGHT_ONE_EDGE] = GestureAction.NAVIGATE_DASHBOARD
    }
    private val savedActions = mutableListOf<SavedAction>()
    private var toolbarTitle = ""

    @Test
    fun `Given available gestures when system edges change then edge rows update without removing multi finger rows`() {
        availableGestures.value = HAGesture.entries.filter { it.pointers != GesturePointers.ONE }
        composeTestRule.setContent {
            HomeAssistantAppTheme {
                GesturesListView(
                    gestureActions = gestureActions,
                    availableGestures = availableGestures.value,
                    onGestureClicked = {},
                )
            }
        }
        assertRowCounts(edge = 0)

        composeTestRule.runOnIdle { availableGestures.value = HAGesture.entries }
        assertRowCounts(edge = 2)
        composeTestRule.runOnIdle {
            availableGestures.value = HAGesture.entries.filterNot { it == HAGesture.SWIPE_RIGHT_ONE_EDGE }
        }
        assertRowCounts(edge = 1)
        edgeRow(GestureAction.QUICKBAR_DEFAULT).assertIsDisplayed()
        edgeRow(GestureAction.NAVIGATE_DASHBOARD).assertDoesNotExist()
        composeTestRule.runOnIdle {
            availableGestures.value = HAGesture.entries.filterNot { it == HAGesture.SWIPE_LEFT_ONE_EDGE }
        }
        assertRowCounts(edge = 1)
        edgeRow(GestureAction.NAVIGATE_DASHBOARD).assertIsDisplayed()
        edgeRow(GestureAction.QUICKBAR_DEFAULT).assertDoesNotExist()
        composeTestRule.runOnIdle {
            availableGestures.value = HAGesture.entries.filter { it.pointers != GesturePointers.ONE }
        }
        assertRowCounts(edge = 0)
    }

    @Test
    fun `Given a visible edge gesture when opening going back and selecting an action then navigation and callback target stay correct`() {
        setScreenContent()
        awaitToolbarTitle(commonR.string.gestures)
        edgeRow(GestureAction.QUICKBAR_DEFAULT).performClick()
        awaitToolbarTitle(HAGesture.SWIPE_LEFT_ONE_EDGE.fullDescription)
        composeTestRule.onNodeWithText(composeTestRule.stringResource(GestureAction.QUICKBAR_DEFAULT.description))
            .assertIsSelected()

        composeTestRule.runOnIdle { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
        awaitToolbarTitle(commonR.string.gestures)
        edgeRow(GestureAction.QUICKBAR_DEFAULT).assertIsDisplayed()
        assertTrue(savedActions.isEmpty())
        edgeRow(GestureAction.QUICKBAR_DEFAULT).performClick()
        awaitToolbarTitle(HAGesture.SWIPE_LEFT_ONE_EDGE.fullDescription)
        composeTestRule.onNodeWithText(composeTestRule.stringResource(GestureAction.SHOW_SIDEBAR.description))
            .performScrollTo()
            .performClick()

        awaitToolbarTitle(commonR.string.gestures)
        assertEquals(listOf(SavedAction(HAGesture.SWIPE_LEFT_ONE_EDGE, GestureAction.SHOW_SIDEBAR)), savedActions)
        edgeRow(GestureAction.SHOW_SIDEBAR).assertIsDisplayed()
    }

    @Test
    fun `Given an open edge action when availability changes then only an unavailable selected gesture returns to the list`() {
        setScreenContent()
        edgeRow(GestureAction.QUICKBAR_DEFAULT).performClick()
        awaitToolbarTitle(HAGesture.SWIPE_LEFT_ONE_EDGE.fullDescription)

        composeTestRule.runOnIdle {
            availableGestures.value = HAGesture.entries.filterNot { it == HAGesture.SWIPE_RIGHT_ONE_EDGE }
        }
        composeTestRule.waitForIdle()
        assertEquals(composeTestRule.stringResource(HAGesture.SWIPE_LEFT_ONE_EDGE.fullDescription), toolbarTitle)
        composeTestRule.onNodeWithText(composeTestRule.stringResource(GestureAction.QUICKBAR_DEFAULT.description))
            .assertIsSelected()
        composeTestRule.runOnIdle {
            availableGestures.value = HAGesture.entries.filter { it.pointers != GesturePointers.ONE }
        }

        awaitToolbarTitle(commonR.string.gestures)
        assertRowCounts(edge = 0)
        assertTrue(savedActions.isEmpty())
        assertEquals(GestureAction.QUICKBAR_DEFAULT, gestureActions[HAGesture.SWIPE_LEFT_ONE_EDGE])
        composeTestRule.runOnIdle { availableGestures.value = HAGesture.entries }
        edgeRow(GestureAction.QUICKBAR_DEFAULT).assertIsDisplayed().performClick()
        awaitToolbarTitle(HAGesture.SWIPE_LEFT_ONE_EDGE.fullDescription)
        assertTrue(savedActions.isEmpty())
    }

    private fun setScreenContent() {
        composeTestRule.setContent {
            HomeAssistantAppTheme {
                GesturesScreen(
                    gestureActions = gestureActions,
                    availableGestures = availableGestures.value,
                    onSetAction = { gesture, action ->
                        savedActions.add(SavedAction(gesture, action))
                        gestureActions[gesture] = action
                    },
                    onToolbarTitleChanged = { toolbarTitle = it },
                )
            }
        }
    }

    private fun assertRowCounts(edge: Int) {
        composeTestRule.onAllNodesWithText(composeTestRule.stringResource(commonR.string.gestures_pointers_one))
            .assertCountEquals(edge)
        composeTestRule.onAllNodesWithText(composeTestRule.stringResource(commonR.string.gestures_pointers_two))
            .assertCountEquals(4)
        composeTestRule.onAllNodesWithText(composeTestRule.stringResource(commonR.string.gestures_pointers_three))
            .assertCountEquals(4)
    }

    private fun edgeRow(action: GestureAction) = composeTestRule.onNode(
        hasText(composeTestRule.stringResource(commonR.string.gestures_pointers_one)) and
            hasText(composeTestRule.stringResource(action.description)),
    )

    private fun awaitToolbarTitle(titleRes: Int) {
        val expected = composeTestRule.stringResource(titleRes)
        composeTestRule.waitUntil { toolbarTitle == expected }
    }

    private data class SavedAction(val gesture: HAGesture, val action: GestureAction)
}
