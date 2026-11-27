package io.homeassistant.companion.android.widgets.button

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.Lightbulb
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.widgets.common.ActionFieldBinder
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class ButtonWidgetConfigureScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Test
    fun `Given empty actionText when displayed then the add action is disabled`() {
        composeTestRule.apply {
            testScreen(emptyState) {
                onNodeWithText(activity.getString(commonR.string.add_widget))
                    .performScrollTo()
                    .assertIsNotEnabled()
            }
        }
    }

    @Test
    fun `Given actionText configured when displayed then the add action is enabled`() {
        composeTestRule.apply {
            testScreen(configuredState) {
                onNodeWithText(activity.getString(commonR.string.add_widget))
                    .performScrollTo()
                    .assertIsEnabled()
            }
        }
    }

    @Test
    fun `Given screen when typing an action text then onActionTextUpdated is triggered`() {
        composeTestRule.apply {
            testScreen(emptyState) {
                onNodeWithText(activity.getString(commonR.string.label_action))
                    .performTextInput("light.turn_on")
                assertEquals("light.turn_on", actionText)
            }
        }
    }

    @Test
    fun `Given screen when typing a label then onLabelUpdated is triggered`() {
        composeTestRule.apply {
            testScreen(configuredState) {
                onNodeWithText(activity.getString(commonR.string.label))
                    .performScrollTo()
                    .performTextReplacement("Toggle Lamp")
                assertEquals("Toggle Lamp", label)
            }
        }
    }

    @Test
    fun `Given dynamic fields when displayed then field labels and values are shown`() {
        composeTestRule.apply {
            val stateWithField = configuredState.copy(
                dynamicFields = listOf(ActionFieldBinder("light.turn_on", "entity_id", "light.living_room")),
            )
            testScreen(stateWithField) {
                onNodeWithText("entity_id").performScrollTo().assertIsDisplayed()
            }
        }
    }

    @Test
    fun `Given a dynamic field when text is updated then onDynamicFieldUpdated is triggered`() {
        composeTestRule.apply {
            val stateWithField = configuredState.copy(
                dynamicFields = listOf(ActionFieldBinder("light.turn_on", "entity_id", "")),
            )
            testScreen(stateWithField) {
                onNodeWithText("entity_id")
                    .performScrollTo()
                    .performTextInput("light.kitchen")
                assertEquals(0, dynamicFieldUpdatedIndex)
                assertEquals("light.kitchen", dynamicFieldUpdatedValue)
            }
        }
    }

    @Test
    fun `Given authentication checkbox when clicked then onRequireAuthenticationChecked is triggered`() {
        composeTestRule.apply {
            testScreen(configuredState.copy(requiresAuthentication = false)) {
                onNode(isToggleable())
                    .performScrollTo()
                    .performClick()
                assertEquals(true, requireAuthenticationChecked)
            }
        }
    }

    @Test
    fun `Given an existing widget when displayed then update widget action button is shown`() {
        composeTestRule.apply {
            testScreen(configuredState.copy(isUpdateWidget = true)) {
                onNodeWithText(activity.getString(commonR.string.update_widget))
                    .performScrollTo()
                    .assertIsDisplayed()
            }
        }
    }

    private class TestHelper {
        var serverSelected: Int? = null
        var actionText: String? = null
        var dynamicFieldUpdatedIndex: Int? = null
        var dynamicFieldUpdatedValue: String? = null
        var iconSelected: Boolean = false
        var label: String? = null
        var backgroundType: WidgetBackgroundType? = null
        var textColor: String? = null
        var requireAuthenticationChecked: Boolean? = null
        var actionClicked = false
        var navigated = false
        var showAddFieldDialog = false
        var showIconDialog = false
    }

    private fun AndroidComposeTestRule<*, *>.testScreen(
        state: ButtonWidgetConfigureState,
        canNavigateBack: Boolean = false,
        dsl: TestHelper.() -> Unit,
    ) {
        TestHelper().apply {
            setContent {
                HATheme {
                    ButtonWidgetConfigureForm(
                        state = state,
                        snackbarHostState = remember { SnackbarHostState() },
                        canNavigateBack = canNavigateBack,
                        onNavigate = { navigated = true },
                        onActionClick = { actionClicked = true },
                        callbacks = ButtonWidgetConfigureCallbacks(
                            onServerSelected = { serverSelected = it },
                            onActionTextUpdated = { actionText = it },
                            onDynamicFieldUpdated = { index, value ->
                                dynamicFieldUpdatedIndex = index
                                dynamicFieldUpdatedValue = value
                            },
                            onIconSelected = { iconSelected = true },
                            onAddFieldDialogOkClicked = { _, _ -> },
                            onLabelUpdated = { label = it },
                            onBackgroundTypeSelected = { backgroundType = it },
                            onTextColorSelected = { textColor = it },
                            onRequireAuthenticationChecked = { requireAuthenticationChecked = it },
                        ),
                        shouldShowAddFieldDialog = { showAddFieldDialog = it },
                        shouldShowIconDialog = { showIconDialog = it },
                    )
                }
            }
            dsl()
        }
    }

    private companion object {
        val emptyState = ButtonWidgetConfigureState(
            serversDropdownItems = listOf(HADropdownItem(key = 1, label = "Home")),
            selectedServerId = 1,
            actionText = "",
        )

        val configuredState = emptyState.copy(
            actionText = "light.turn_on",
            label = "Turn on light",
            selectedIcon = Mdi.Lightbulb,
            selectedBackgroundType = WidgetBackgroundType.TRANSPARENT,
            textColorHex = "#3A3A3A",
        )
    }
}
