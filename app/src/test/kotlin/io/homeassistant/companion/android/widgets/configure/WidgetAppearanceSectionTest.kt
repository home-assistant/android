package io.homeassistant.companion.android.widgets.configure

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
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
class WidgetAppearanceSectionTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Test
    fun `Given a non transparent background when displayed then the text color dropdown is hidden`() {
        composeTestRule.apply {
            testSection(selectedBackgroundType = WidgetBackgroundType.DAYNIGHT) {
                onNodeWithText(activity.getString(commonR.string.widget_text_color_label)).assertDoesNotExist()
            }
        }
    }

    @Test
    fun `Given a transparent background when displayed then the text color dropdown is shown`() {
        composeTestRule.apply {
            testSection(selectedBackgroundType = WidgetBackgroundType.TRANSPARENT) {
                onNodeWithText(activity.getString(commonR.string.widget_text_color_label), substring = true)
                    .assertIsDisplayed()
            }
        }
    }

    @Test
    fun `Given dynamic color available when the background dropdown is opened then dynamic color is offered`() {
        composeTestRule.apply {
            testSection(dynamicColorAvailable = true) {
                openBackgroundTypeDropdown()
                onNodeWithText(activity.getString(commonR.string.widget_background_type_dynamiccolor)).assertIsDisplayed()
            }
        }
    }

    @Test
    fun `Given dynamic color unavailable when the background dropdown is opened then dynamic color is absent`() {
        composeTestRule.apply {
            testSection(dynamicColorAvailable = false) {
                openBackgroundTypeDropdown()
                onNodeWithText(activity.getString(commonR.string.widget_background_type_dynamiccolor)).assertDoesNotExist()
            }
        }
    }

    @Test
    fun `Given section when a background type is picked then onBackgroundTypeSelected is triggered`() {
        composeTestRule.apply {
            testSection(selectedBackgroundType = WidgetBackgroundType.DAYNIGHT) {
                openBackgroundTypeDropdown()
                onNodeWithText(activity.getString(commonR.string.widget_background_type_transparent)).performClick()
                assertEquals(WidgetBackgroundType.TRANSPARENT, backgroundType)
            }
        }
    }

    @Test
    fun `Given a transparent background when a text color is picked then onTextColorSelected is triggered with its hex`() {
        composeTestRule.apply {
            testSection(selectedBackgroundType = WidgetBackgroundType.TRANSPARENT) {
                onNodeWithText(activity.getString(commonR.string.widget_text_color_label), substring = true).performClick()
                // The unset default follows the system theme (BLACK in the light test environment),
                // so pick WHITE to guarantee a change that fires the callback.
                onNodeWithText(activity.getString(commonR.string.widget_text_color_white)).performClick()
                assertEquals(WidgetTextColor.WHITE.resolve(activity), textColor)
            }
        }
    }

    @Test
    fun `Given a persisted text color when displayed then the matching color is selected`() {
        composeTestRule.apply {
            testSection(
                selectedBackgroundType = WidgetBackgroundType.TRANSPARENT,
                textColorHex = WidgetTextColor.WHITE.resolve(activity),
            ) {
                onNodeWithText(activity.getString(commonR.string.widget_text_color_white)).assertIsDisplayed()
            }
        }
    }

    private class TestHelper {
        var backgroundType: WidgetBackgroundType? = null
        var textColor: String? = null
    }

    private fun AndroidComposeTestRule<*, *>.openBackgroundTypeDropdown() {
        onNodeWithText(activity.getString(commonR.string.widget_background_type_label), substring = true).performClick()
    }

    private fun AndroidComposeTestRule<*, *>.testSection(
        selectedBackgroundType: WidgetBackgroundType = WidgetBackgroundType.DAYNIGHT,
        dynamicColorAvailable: Boolean = false,
        textColorHex: String? = null,
        dsl: TestHelper.() -> Unit,
    ) {
        TestHelper().apply {
            setContent {
                HATheme {
                    Column {
                        WidgetAppearanceSection(
                            selectedBackgroundType = selectedBackgroundType,
                            dynamicColorAvailable = dynamicColorAvailable,
                            textColorHex = textColorHex,
                            onBackgroundTypeSelected = { backgroundType = it },
                            onTextColorSelected = { textColor = it },
                        )
                    }
                }
            }
            dsl()
        }
    }
}
