package io.homeassistant.companion.android.widgets.template

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class TemplateWidgetConfigureScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Test
    fun `Given no rendered template when displayed then the action is disabled`() {
        composeTestRule.apply {
            testScreen(singleServerState) {
                onNodeWithText(activity.getString(commonR.string.empty_template)).assertIsDisplayed()
                onNodeWithText(activity.getString(commonR.string.add_widget))
                    .performScrollTo()
                    .assertIsNotEnabled()
            }
        }
    }

    @Test
    fun `Given a rendered template when displayed then the action is enabled`() {
        composeTestRule.apply {
            testScreen(renderedState) {
                onNodeWithText(activity.getString(commonR.string.add_widget))
                    .performScrollTo()
                    .assertIsEnabled()
            }
        }
    }

    @Test
    fun `Given screen when typing a template then onTemplateChanged is triggered`() {
        composeTestRule.apply {
            testScreen(singleServerState) {
                onNodeWithText(activity.getString(commonR.string.template))
                    .performTextInput("{{ 1 }}")
                assertEquals("{{ 1 }}", template)
            }
        }
    }

    @Test
    fun `Given screen when typing a text size then onTextSizeChanged is triggered`() {
        composeTestRule.apply {
            testScreen(singleServerState) {
                onNodeWithText(activity.getString(commonR.string.widget_text_size_label), substring = true)
                    .performScrollTo()
                    .performTextReplacement("24")
                assertEquals("24", textSize)
            }
        }
    }

    @Test
    fun `Given a rendered template when displayed then the preview shows the rendered text`() {
        composeTestRule.apply {
            testScreen(renderedState) {
                onNodeWithText("42").assertIsDisplayed()
            }
        }
    }

    @Test
    fun `Given a template render error when displayed then the preview shows the error message and action disabled`() {
        composeTestRule.apply {
            testScreen(
                singleServerState.copy(
                    template = "{{ broken",
                    preview = TemplatePreview.Error(commonR.string.template_render_error),
                ),
            ) {
                onNodeWithText(activity.getString(commonR.string.template_render_error)).assertIsDisplayed()
                onNodeWithText(activity.getString(commonR.string.add_widget))
                    .performScrollTo()
                    .assertIsNotEnabled()
            }
        }
    }

    @Test
    fun `Given an invalid text size when displayed then the accepted values are explained`() {
        composeTestRule.apply {
            testScreen(renderedState.copy(textSize = "")) {
                onNodeWithText(activity.getString(commonR.string.widget_text_size_error))
                    .performScrollTo()
                    .assertIsDisplayed()
                onNodeWithText(activity.getString(commonR.string.add_widget))
                    .performScrollTo()
                    .assertIsNotEnabled()
            }
        }
    }

    private class TestHelper {
        var serverSelected: Int? = null
        var template: String? = null
        var textSize: String? = null
        var backgroundType: WidgetBackgroundType? = null
        var textColor: String? = null
        var actionClicked = false
        var navigated = false
    }

    private fun AndroidComposeTestRule<*, *>.testScreen(
        state: TemplateWidgetConfigureState,
        canNavigateBack: Boolean = false,
        dsl: TestHelper.() -> Unit,
    ) {
        TestHelper().apply {
            setContent {
                HATheme {
                    TemplateWidgetConfigureContent(
                        state = state,
                        snackbarHostState = remember { SnackbarHostState() },
                        canNavigateBack = canNavigateBack,
                        onNavigate = { navigated = true },
                        onServerSelected = { serverSelected = it },
                        onTemplateChanged = { template = it },
                        onTextSizeChanged = { textSize = it },
                        onBackgroundTypeSelected = { backgroundType = it },
                        onTextColorSelected = { textColor = it },
                        onActionClick = { actionClicked = true },
                    )
                }
            }
            dsl()
        }
    }
}

private val singleServerState = TemplateWidgetConfigureState(
    serversDropdownItems = listOf(HADropdownItem(key = 1, label = "Home")),
    selectedServerId = 1,
)

private val renderedState = singleServerState.copy(
    template = "{{ states('sensor.example') }}",
    preview = TemplatePreview.Rendered("42"),
)
