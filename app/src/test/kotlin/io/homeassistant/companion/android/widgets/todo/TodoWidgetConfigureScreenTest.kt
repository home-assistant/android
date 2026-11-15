package io.homeassistant.companion.android.widgets.todo

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
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
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
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

    private val listEntity = Entity(
        entityId = "todo.shopping_list",
        state = "3",
        attributes = emptyMap(),
        lastChanged = LocalDateTime.MIN,
        lastUpdated = LocalDateTime.MIN,
    )

    private val newWidgetState = TodoWidgetConfigureState(
        serversDropdownItems = listOf(HADropdownItem(key = 1, label = "Home")),
        selectedServerId = 1,
        entityDisplayState = EntityDisplayState.Loaded(
            listOf(EntityDisplayWithContext(EntityDisplayWithoutContext(listEntity, name = "Shopping List"))),
        ),
    )

    private val configuredState = newWidgetState.copy(
        selectedEntityId = listEntity.entityId,
        isUpdateWidget = true,
    )

    @Test
    fun `Given no list selected for new widget when displayed then the add action is disabled and the configuration is hidden`() {
        composeTestRule.apply {
            testScreen(newWidgetState) {
                onNodeWithText(activity.getString(commonR.string.add_widget))
                    .performScrollTo()
                    .assertIsNotEnabled()
                onNodeWithText(activity.getString(commonR.string.widget_todo_show_completed)).assertDoesNotExist()
                onNodeWithText(activity.getString(commonR.string.widget_background_type_label))
                    .assertDoesNotExist()
            }
        }
    }

    @Test
    fun `Given a list selected for new widget when displayed then the configuration is shown`() {
        composeTestRule.apply {
            testScreen(configuredState) {
                onNodeWithText(activity.getString(commonR.string.widget_todo_show_completed))
                    .performScrollTo()
                    .assertIsDisplayed()
                onNodeWithText(activity.getString(commonR.string.widget_background_type_label))
                    .performScrollTo()
                    .assertIsDisplayed()
            }
        }
    }

    @Test
    fun `Given a configured widget when displayed then the action is enabled`() {
        composeTestRule.apply {
            testScreen(configuredState) {
                onNodeWithText(activity.getString(commonR.string.update_widget))
                    .performScrollTo()
                    .assertIsEnabled()
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
}
