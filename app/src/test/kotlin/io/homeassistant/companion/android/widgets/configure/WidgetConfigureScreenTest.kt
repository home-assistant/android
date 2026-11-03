package io.homeassistant.companion.android.widgets.configure

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.theme.HATheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
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
class WidgetConfigureScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Test
    fun `Given a new state emitted when collected then the content renders it`() {
        composeTestRule.apply {
            val viewModel = FakeViewModel(FakeState(label = "Initial"))
            setScreen(viewModel)

            onNodeWithText("Initial").assertIsDisplayed()

            runOnUiThread { viewModel.mutableState.value = FakeState(label = "Updated") }
            waitForIdle()

            onNodeWithText("Updated").assertIsDisplayed()
        }
    }

    @Test
    fun `Given an error emitted when collected then it is shown in a snackbar`() {
        composeTestRule.apply {
            val viewModel = FakeViewModel(FakeState(label = "Initial"))
            val message = activity.getString(commonR.string.widget_creation_error)

            // Freeze the clock so the snackbar does not auto-dismiss before the assertion.
            mainClock.autoAdvance = false
            setScreen(viewModel)
            mainClock.advanceTimeByFrame()

            runOnUiThread { viewModel.errorChannel.trySend(commonR.string.widget_creation_error) }
            // Let the collector resume and the snackbar animate in.
            mainClock.advanceTimeBy(SNACKBAR_SETTLE_MS)

            onNodeWithText(message).assertIsDisplayed()
        }
    }

    @Test
    fun `Given the scaffold is the task root when displayed then it offers close`() {
        composeTestRule.apply {
            testScaffold(singleServerState, canNavigateBack = false) {
                onNodeWithContentDescription(activity.getString(commonR.string.navigate_up)).assertDoesNotExist()
                onNodeWithContentDescription(activity.getString(commonR.string.close)).performClick()
                assertTrue(navigated)
            }
        }
    }

    @Test
    fun `Given the scaffold has a back stack when displayed then it offers back`() {
        composeTestRule.apply {
            testScaffold(singleServerState, canNavigateBack = true) {
                onNodeWithContentDescription(activity.getString(commonR.string.close)).assertDoesNotExist()
                onNodeWithContentDescription(activity.getString(commonR.string.navigate_up)).performClick()
                assertTrue(navigated)
            }
        }
    }

    @Test
    fun `Given a new widget when displayed then the add action is shown`() {
        composeTestRule.apply {
            testScaffold(singleServerState) {
                onNodeWithText(activity.getString(commonR.string.add_widget)).performScrollTo().assertIsDisplayed()
                onNodeWithText(activity.getString(commonR.string.update_widget)).assertDoesNotExist()
            }
        }
    }

    @Test
    fun `Given an existing widget when displayed then the update action is shown`() {
        composeTestRule.apply {
            testScaffold(singleServerState.copy(isUpdateWidget = true)) {
                onNodeWithText(activity.getString(commonR.string.update_widget)).performScrollTo().assertIsDisplayed()
                onNodeWithText(activity.getString(commonR.string.add_widget)).assertDoesNotExist()
            }
        }
    }

    @Test
    fun `Given the action is disabled when displayed then the action cannot be clicked`() {
        composeTestRule.apply {
            testScaffold(singleServerState.copy(isActionEnabled = false)) {
                onNodeWithText(activity.getString(commonR.string.add_widget)).performScrollTo().assertIsNotEnabled()
            }
        }
    }

    @Test
    fun `Given the action is enabled when the action is clicked then onActionClick is triggered`() {
        composeTestRule.apply {
            testScaffold(singleServerState) {
                onNodeWithText(activity.getString(commonR.string.add_widget)).performScrollTo().performClick()
                assertTrue(actionClicked)
            }
        }
    }

    @Test
    fun `Given a single server when displayed then the server selector is hidden`() {
        composeTestRule.apply {
            testScaffold(singleServerState) {
                onNodeWithText(activity.getString(commonR.string.server_select)).assertDoesNotExist()
            }
        }
    }

    @Test
    fun `Given several servers when displayed then the server selector is shown`() {
        composeTestRule.apply {
            testScaffold(multipleServersState) {
                onNodeWithText(activity.getString(commonR.string.server_select), substring = true)
                    .performScrollTo()
                    .assertIsDisplayed()
            }
        }
    }

    @Test
    fun `Given several servers when a server is picked then onServerSelected is triggered`() {
        composeTestRule.apply {
            testScaffold(multipleServersState) {
                onNodeWithText(activity.getString(commonR.string.server_select), substring = true).performClick()
                onNodeWithText("Vacation home").performClick()
                assertEquals(2, serverSelected)
            }
        }
    }

    @Test
    fun `Given a scaffold when displayed then the content is shown between the selector and the action`() {
        composeTestRule.apply {
            testScaffold(multipleServersState) {
                onNodeWithText("Content").performScrollTo().assertIsDisplayed()
            }
        }
    }

    private fun AndroidComposeTestRule<*, *>.setScreen(viewModel: FakeViewModel) {
        setContent {
            HATheme {
                WidgetConfigureScreen(viewModel = viewModel) { state, snackbarHostState ->
                    Column {
                        Text(state.label)
                        SnackbarHost(hostState = snackbarHostState)
                    }
                }
            }
        }
    }

    private class TestHelper {
        var serverSelected: Int? = null
        var actionClicked = false
        var navigated = false
    }

    private fun AndroidComposeTestRule<*, *>.testScaffold(
        state: FakeState,
        canNavigateBack: Boolean = false,
        dsl: TestHelper.() -> Unit,
    ) {
        TestHelper().apply {
            setContent {
                HATheme {
                    WidgetConfigureScaffold(
                        title = commonR.string.select_entity_to_display,
                        state = state,
                        snackbarHostState = remember { SnackbarHostState() },
                        canNavigateBack = canNavigateBack,
                        onNavigate = { navigated = true },
                        onServerSelected = { serverSelected = it },
                        onActionClick = { actionClicked = true },
                    ) {
                        Text(state.label)
                    }
                }
            }
            dsl()
        }
    }

    private class FakeViewModel(initialState: FakeState) : WidgetConfigureViewModel<FakeState> {
        val mutableState = MutableStateFlow(initialState)
        override val state: StateFlow<FakeState> = mutableState

        val errorChannel = Channel<Int>(Channel.BUFFERED)
        override val errors = errorChannel.receiveAsFlow()
    }

    private data class FakeState(
        val label: String,
        override val serversDropdownItems: List<HADropdownItem<Int>> = emptyList(),
        override val selectedServerId: Int = 0,
        override val isUpdateWidget: Boolean = false,
        override val isActionEnabled: Boolean = true,
    ) : WidgetConfigureState

    private companion object {
        // Enough virtual time to run the error collector and play the snackbar enter animation, while
        // staying well within the snackbar's visible window.
        const val SNACKBAR_SETTLE_MS = 1_000L

        val singleServerState = FakeState(
            label = "Content",
            serversDropdownItems = listOf(HADropdownItem(key = 1, label = "Home")),
            selectedServerId = 1,
        )

        val multipleServersState = singleServerState.copy(
            serversDropdownItems = listOf(
                HADropdownItem(key = 1, label = "Home"),
                HADropdownItem(key = 2, label = "Vacation home"),
            ),
        )
    }
}
