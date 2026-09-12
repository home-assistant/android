package io.homeassistant.companion.android.widgets.todo

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
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
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithContext
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithoutContext
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import java.time.LocalDateTime
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
class TodoWidgetConfigureScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Test
    fun `Given no list selected when displayed then the add action is disabled and the configuration is hidden`() {
        composeTestRule.apply {
            testScreen(newWidgetState) {
                onNodeWithText(activity.getString(commonR.string.add_widget))
                    .performScrollTo()
                    .assertIsNotEnabled()
                onNodeWithText(activity.getString(commonR.string.widget_todo_show_completed)).assertDoesNotExist()
                onNodeWithText(activity.getString(commonR.string.widget_background_type_label), substring = true)
                    .assertDoesNotExist()
            }
        }
    }

    @Test
    fun `Given a list selected when displayed then the configuration is shown`() {
        composeTestRule.apply {
            testScreen(configuredState) {
                onNodeWithText(activity.getString(commonR.string.widget_todo_show_completed))
                    .performScrollTo()
                    .assertIsDisplayed()
                onNodeWithText(activity.getString(commonR.string.widget_background_type_label), substring = true)
                    .performScrollTo()
                    .assertIsDisplayed()
            }
        }
    }

    @Test
    fun `Given an existing widget when displayed then the update action is enabled`() {
        composeTestRule.apply {
            testScreen(configuredState) {
                onNodeWithText(activity.getString(commonR.string.update_widget))
                    .performScrollTo()
                    .assertIsEnabled()
            }
        }
    }

    @Test
    fun `Given the screen is the task root when displayed then it offers close`() {
        composeTestRule.apply {
            testScreen(configuredState, canNavigateBack = false) {
                onNodeWithContentDescription(activity.getString(commonR.string.navigate_up)).assertDoesNotExist()
                onNodeWithContentDescription(activity.getString(commonR.string.close)).performClick()
                assertTrue(navigated)
            }
        }
    }

    @Test
    fun `Given the screen has a back stack when displayed then it offers back`() {
        composeTestRule.apply {
            testScreen(configuredState, canNavigateBack = true) {
                onNodeWithContentDescription(activity.getString(commonR.string.close)).assertDoesNotExist()
                onNodeWithContentDescription(activity.getString(commonR.string.navigate_up)).performClick()
                assertTrue(navigated)
            }
        }
    }

    @Test
    fun `Given a valid configuration when the action is clicked then onActionClick is triggered`() {
        composeTestRule.apply {
            testScreen(configuredState) {
                onNodeWithText(activity.getString(commonR.string.update_widget))
                    .performScrollTo()
                    .performClick()
                assertTrue(actionClicked)
            }
        }
    }

    @Test
    fun `Given show completed enabled when the row is clicked then onShowCompletedChanged is triggered with false`() {
        composeTestRule.apply {
            testScreen(configuredState.copy(showCompleted = true)) {
                onNodeWithText(activity.getString(commonR.string.widget_todo_show_completed))
                    .performScrollTo()
                    .performClick()
                assertEquals(false, showCompleted)
            }
        }
    }

    @Test
    fun `Given a single server when displayed then the server selector is hidden`() {
        composeTestRule.apply {
            testScreen(configuredState) {
                onNodeWithText(activity.getString(commonR.string.server_select)).assertDoesNotExist()
            }
        }
    }

    @Test
    fun `Given several servers when displayed then the server selector is shown`() {
        composeTestRule.apply {
            testScreen(multipleServersState) {
                onNodeWithText(activity.getString(commonR.string.server_select), substring = true)
                    .performScrollTo()
                    .assertIsDisplayed()
            }
        }
    }

    @Test
    fun `Given a transparent background when displayed then the text color selector is shown`() {
        composeTestRule.apply {
            testScreen(configuredState.copy(selectedBackgroundType = WidgetBackgroundType.TRANSPARENT)) {
                onNodeWithText(activity.getString(commonR.string.widget_text_color_label), substring = true)
                    .performScrollTo()
                    .assertIsDisplayed()
            }
        }
    }

    @Test
    fun `Given an opaque background when displayed then the text color selector is hidden`() {
        composeTestRule.apply {
            testScreen(configuredState.copy(selectedBackgroundType = WidgetBackgroundType.DAYNIGHT)) {
                onNodeWithText(activity.getString(commonR.string.widget_text_color_label), substring = true)
                    .assertDoesNotExist()
            }
        }
    }

    private class TestHelper {
        var serverSelected: Int? = null
        var entitySelected: String? = null
        var showCompleted: Boolean? = null
        var backgroundType: WidgetBackgroundType? = null
        var textColor: String? = null
        var actionClicked = false
        var navigated = false
    }

    private fun AndroidComposeTestRule<*, *>.testScreen(
        state: TodoWidgetConfigureState,
        canNavigateBack: Boolean = false,
        dsl: TestHelper.() -> Unit,
    ) {
        TestHelper().apply {
            setContent {
                HATheme {
                    TodoWidgetConfigureContent(
                        state = state,
                        snackbarHostState = remember { SnackbarHostState() },
                        canNavigateBack = canNavigateBack,
                        onNavigate = { navigated = true },
                        onServerSelected = { serverSelected = it },
                        onEntitySelected = { entitySelected = it },
                        onShowCompletedChanged = { showCompleted = it },
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
        val ENTITY = Entity(
            entityId = "todo.shopping_list",
            state = "3",
            attributes = emptyMap(),
            lastChanged = LocalDateTime.MIN,
            lastUpdated = LocalDateTime.MIN,
        )

        val newWidgetState = TodoWidgetConfigureState(
            serversDropdownItems = listOf(HADropdownItem(key = 1, label = "Home")),
            selectedServerId = 1,
            entityDisplayState = EntityDisplayState.Loaded(
                listOf(EntityDisplayWithContext(EntityDisplayWithoutContext(ENTITY, name = "Shopping List"))),
            ),
        )

        val configuredState = newWidgetState.copy(
            selectedEntityId = ENTITY.entityId,
            isUpdateWidget = true,
        )

        val multipleServersState = configuredState.copy(
            serversDropdownItems = listOf(
                HADropdownItem(key = 1, label = "Home"),
                HADropdownItem(key = 2, label = "Vacation home"),
            ),
        )
    }
}
