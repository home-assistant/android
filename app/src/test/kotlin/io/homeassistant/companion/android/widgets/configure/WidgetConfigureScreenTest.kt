package io.homeassistant.companion.android.widgets.configure

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
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
    }
}
