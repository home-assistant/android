package io.homeassistant.companion.android.widgets.entity

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
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
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithContext
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithoutContext
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.database.widget.WidgetTapAction
import java.time.LocalDateTime
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
class EntityWidgetConfigureScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Test
    fun `Given no entity selected when displayed then the add action is disabled`() {
        composeTestRule.apply {
            testScreen(newWidgetState) {
                onNodeWithText(activity.getString(commonR.string.add_widget))
                    .performScrollTo()
                    .assertIsNotEnabled()
            }
        }
    }

    @Test
    fun `Given no entity selected when displayed then the configuration is hidden`() {
        composeTestRule.apply {
            testScreen(newWidgetState) {
                onNodeWithText(activity.getString(commonR.string.widget_attribute_add), substring = true)
                    .assertDoesNotExist()
                onNodeWithText(activity.getString(commonR.string.widget_text_size_label), substring = true)
                    .assertDoesNotExist()
                onNodeWithText(activity.getString(commonR.string.widget_background_type_label), substring = true)
                    .assertDoesNotExist()
            }
        }
    }

    @Test
    fun `Given an entity selected when displayed then the configuration is shown`() {
        composeTestRule.apply {
            testScreen(configuredState) {
                onNodeWithText(activity.getString(commonR.string.widget_text_size_label), substring = true)
                    .performScrollTo()
                    .assertIsDisplayed()
                onNodeWithText(activity.getString(commonR.string.widget_background_type_label), substring = true)
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
    fun `Given an invalid text size when displayed then the accepted values are explained`() {
        composeTestRule.apply {
            testScreen(configuredState.copy(textSize = "")) {
                onNodeWithText(activity.getString(commonR.string.widget_text_size_error))
                    .performScrollTo()
                    .assertIsDisplayed()
            }
        }
    }

    @Test
    fun `Given a non toggleable entity when displayed then the tap action is hidden`() {
        composeTestRule.apply {
            testScreen(configuredState.copy(selectedEntityId = "sensor.temperature")) {
                onNodeWithText(activity.getString(commonR.string.widget_tap_action_label)).assertDoesNotExist()
            }
        }
    }

    @Test
    fun `Given screen when typing a label then onLabelChanged is triggered`() {
        composeTestRule.apply {
            testScreen(configuredState.copy(label = "")) {
                onNodeWithText(activity.getString(commonR.string.label_label), substring = true)
                    .performScrollTo()
                    .performTextInput("Kitchen")
                assertEquals("Kitchen", label)
            }
        }
    }

    @Test
    fun `Given screen when typing a text size then onTextSizeChanged is triggered`() {
        composeTestRule.apply {
            testScreen(configuredState.copy(textSize = "")) {
                onNodeWithText(activity.getString(commonR.string.widget_text_size_label), substring = true)
                    .performScrollTo()
                    .performTextInput("24")
                assertEquals("24", textSize)
            }
        }
    }

    @Test
    fun `Given screen when typing a state separator then onStateSeparatorChanged is triggered`() {
        composeTestRule.apply {
            testScreen(configuredState.copy(stateSeparator = "")) {
                onNodeWithText(activity.getString(commonR.string.widget_state_separator_label), substring = true)
                    .performScrollTo()
                    .performTextInput("|")
                assertEquals("|", stateSeparator)
            }
        }
    }

    @Test
    fun `Given screen when typing an attribute separator then onAttributeSeparatorChanged is triggered`() {
        composeTestRule.apply {
            testScreen(configuredState.copy(attributeSeparator = "")) {
                onNodeWithText(activity.getString(commonR.string.widget_attribute_separator_label), substring = true)
                    .performScrollTo()
                    .performTextInput(";")
                assertEquals(";", attributeSeparator)
            }
        }
    }

    @Test
    fun `Given screen when typing a custom attribute then onCustomAttributeChanged is triggered`() {
        composeTestRule.apply {
            testScreen(configuredState) {
                onNodeWithText(activity.getString(commonR.string.widget_attribute_add), substring = true)
                    .performScrollTo()
                    .performTextInput("power, current")
                assertEquals("power, current", customAttribute)
            }
        }
    }

    @Test
    fun `Given a typed custom attribute when add is clicked then onCustomAttributesAdded is triggered`() {
        composeTestRule.apply {
            testScreen(configuredState.copy(customAttribute = "power, current")) {
                onAllNodesWithContentDescription(activity.getString(commonR.string.widget_attribute_add))[0]
                    .performScrollTo()
                    .performClick()
                assertTrue(customAttributesAdded)
            }
        }
    }

    @Test
    fun `Given an unselected attribute when its chip is clicked then onAttributeAdded is triggered`() {
        composeTestRule.apply {
            testScreen(configuredState) {
                onNodeWithText("power").performScrollTo().performClick()
                assertEquals("power", attributeAdded)
            }
        }
    }

    @Test
    fun `Given a selected attribute when its chip is clicked then onAttributeRemoved is triggered`() {
        composeTestRule.apply {
            testScreen(configuredState) {
                onNodeWithText("brightness").performScrollTo().performClick()
                assertEquals("brightness", attributeRemoved)
            }
        }
    }

    private class TestHelper {
        var serverSelected: Int? = null
        var entitySelected: String? = null
        var attributeAdded: String? = null
        var attributeRemoved: String? = null
        var customAttribute: String? = null
        var customAttributesAdded = false
        var label: String? = null
        var textSize: String? = null
        var stateSeparator: String? = null
        var attributeSeparator: String? = null
        var tapAction: WidgetTapAction? = null
        var backgroundType: WidgetBackgroundType? = null
        var textColor: String? = null
        var actionClicked = false
        var navigated = false
    }

    private fun AndroidComposeTestRule<*, *>.testScreen(
        state: EntityWidgetConfigureState,
        canNavigateBack: Boolean = false,
        dsl: TestHelper.() -> Unit,
    ) {
        TestHelper().apply {
            setContent {
                HATheme {
                    EntityWidgetConfigureContent(
                        state = state,
                        snackbarHostState = remember { SnackbarHostState() },
                        canNavigateBack = canNavigateBack,
                        onNavigate = { navigated = true },
                        onServerSelected = { serverSelected = it },
                        onEntitySelected = { entitySelected = it },
                        onAttributeAdded = { attributeAdded = it },
                        onAttributeRemoved = { attributeRemoved = it },
                        onCustomAttributeChanged = { customAttribute = it },
                        onCustomAttributesAdded = { customAttributesAdded = true },
                        onLabelChanged = { label = it },
                        onTextSizeChanged = { textSize = it },
                        onStateSeparatorChanged = { stateSeparator = it },
                        onAttributeSeparatorChanged = { attributeSeparator = it },
                        onTapActionSelected = { tapAction = it },
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
            entityId = "light.office",
            state = "on",
            attributes = mapOf("power" to "50W"),
            lastChanged = LocalDateTime.MIN,
            lastUpdated = LocalDateTime.MIN,
        )

        val newWidgetState = EntityWidgetConfigureState(
            serversDropdownItems = listOf(HADropdownItem(key = 1, label = "Home")),
            selectedServerId = 1,
            entityDisplayState = EntityDisplayState.Loaded(listOf(EntityDisplayWithContext(EntityDisplayWithoutContext(ENTITY)))),
        )

        val configuredState = newWidgetState.copy(
            selectedEntityId = ENTITY.entityId,
            availableAttributes = listOf("brightness", "power"),
            selectedAttributeIds = listOf("brightness"),
            label = "Office light",
            stateSeparator = " - ",
            attributeSeparator = ", ",
            selectedTapAction = WidgetTapAction.TOGGLE,
            isUpdateWidget = true,
        )
    }
}
