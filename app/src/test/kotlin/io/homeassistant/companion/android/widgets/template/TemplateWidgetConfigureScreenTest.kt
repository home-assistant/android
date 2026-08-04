package io.homeassistant.companion.android.widgets.template

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
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
class TemplateWidgetConfigureScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Test
    fun `Given no rendered template when displayed then the action is disabled`() {
        composeTestRule.apply {
            testScreen(singleServerState) {
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
    fun `Given an existing widget when displayed then the update action is shown`() {
        composeTestRule.apply {
            testScreen(renderedState.copy(isUpdateWidget = true)) {
                onNodeWithText(activity.getString(commonR.string.update_widget))
                    .performScrollTo()
                    .assertIsEnabled()
            }
        }
    }

    @Test
    fun `Given the screen is the task root when displayed then it offers close`() {
        composeTestRule.apply {
            testScreen(singleServerState, canNavigateBack = false) {
                onNodeWithContentDescription(activity.getString(commonR.string.navigate_up)).assertDoesNotExist()
                onNodeWithContentDescription(activity.getString(commonR.string.close)).performClick()
                assertTrue(navigated)
            }
        }
    }

    @Test
    fun `Given the screen has a back stack when displayed then it offers back`() {
        composeTestRule.apply {
            testScreen(singleServerState, canNavigateBack = true) {
                onNodeWithContentDescription(activity.getString(commonR.string.close)).assertDoesNotExist()
                onNodeWithContentDescription(activity.getString(commonR.string.navigate_up)).performClick()
                assertTrue(navigated)
            }
        }
    }

    @Test
    fun `Given a valid configuration when the action is clicked then onActionClick is triggered`() {
        composeTestRule.apply {
            testScreen(renderedState) {
                onNodeWithText(activity.getString(commonR.string.add_widget))
                    .performScrollTo()
                    .performClick()
                assertTrue(actionClicked)
            }
        }
    }

    @Test
    fun `Given a single server when displayed then the server selector is hidden`() {
        composeTestRule.apply {
            testScreen(singleServerState) {
                onNodeWithText(activity.getString(commonR.string.server_select)).assertDoesNotExist()
            }
        }
    }

    @Test
    fun `Given several servers when displayed then the server selector is shown`() {
        composeTestRule.apply {
            testScreen(multipleServersState) {
                onNodeWithText(activity.getString(commonR.string.server_select), substring = true)
                    .assertIsDisplayed()
            }
        }
    }

    @Test
    fun `Given several servers when a server is picked then onServerSelected is triggered`() {
        composeTestRule.apply {
            testScreen(multipleServersState) {
                onNodeWithText(activity.getString(commonR.string.server_select), substring = true)
                    .performClick()
                onNodeWithText("Vacation home").performClick()
                assertEquals(2, serverSelected)
            }
        }
    }

    @Test
    fun `Given screen when typing a template then onTemplateChanged is triggered`() {
        composeTestRule.apply {
            testScreen(singleServerState) {
                onNodeWithText(activity.getString(commonR.string.template), substring = true)
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
                    .performTextInput("24")
                assertEquals("24", textSize)
            }
        }
    }

    @Test
    fun `Given an empty template when displayed then the preview shows the empty message`() {
        composeTestRule.apply {
            testScreen(singleServerState) {
                onNodeWithText(activity.getString(commonR.string.empty_template)).assertIsDisplayed()
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
    fun `Given a template render error when displayed then the preview shows the error message`() {
        composeTestRule.apply {
            testScreen(
                singleServerState.copy(
                    template = "{{ broken",
                    preview = TemplatePreview.Error(commonR.string.template_render_error),
                ),
            ) {
                onNodeWithText(activity.getString(commonR.string.template_render_error)).assertIsDisplayed()
            }
        }
    }

    @Test
    fun `Given a non transparent background when displayed then the text color dropdown is hidden`() {
        composeTestRule.apply {
            testScreen(singleServerState.copy(selectedBackgroundType = WidgetBackgroundType.DAYNIGHT)) {
                onNodeWithText(activity.getString(commonR.string.widget_text_color_label)).assertDoesNotExist()
            }
        }
    }

    @Test
    fun `Given a transparent background when displayed then the text color dropdown is shown`() {
        composeTestRule.apply {
            testScreen(singleServerState.copy(selectedBackgroundType = WidgetBackgroundType.TRANSPARENT)) {
                onNodeWithText(activity.getString(commonR.string.widget_text_color_label), substring = true)
                    .performScrollTo()
                    .assertIsDisplayed()
            }
        }
    }

    @Test
    fun `Given screen when a background type is picked then onBackgroundTypeSelected is triggered`() {
        composeTestRule.apply {
            testScreen(singleServerState.copy(selectedBackgroundType = WidgetBackgroundType.DAYNIGHT)) {
                onNodeWithText(activity.getString(commonR.string.widget_background_type_label), substring = true)
                    .performScrollTo()
                    .performClick()
                onNodeWithText(activity.getString(commonR.string.widget_background_type_transparent))
                    .performClick()
                assertEquals(WidgetBackgroundType.TRANSPARENT, backgroundType)
            }
        }
    }

    @Test
    fun `Given a transparent background when a text color is picked then onTextColorSelected is triggered`() {
        composeTestRule.apply {
            testScreen(singleServerState.copy(selectedBackgroundType = WidgetBackgroundType.TRANSPARENT)) {
                onNodeWithText(activity.getString(commonR.string.widget_text_color_label), substring = true)
                    .performScrollTo()
                    .performClick()
                onNodeWithText(activity.getString(commonR.string.widget_text_color_black))
                    .performClick()
                assertTrue(textColor != null)
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

    private companion object {
        val singleServerState = TemplateWidgetConfigureState(
            serversDropdownItems = listOf(HADropdownItem(key = 1, label = "Home")),
            selectedServerId = 1,
        )

        val multipleServersState = singleServerState.copy(
            serversDropdownItems = listOf(
                HADropdownItem(key = 1, label = "Home"),
                HADropdownItem(key = 2, label = "Vacation home"),
            ),
        )

        val renderedState = singleServerState.copy(
            template = "{{ states('sensor.example') }}",
            preview = TemplatePreview.Rendered("42"),
        )
    }
}
