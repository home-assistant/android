package io.homeassistant.companion.android.settings.wear.views

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import org.junit.Rule
import org.junit.Test

class SettingsWearTopAppBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `Given a wear settings screen when it renders without a docs link then no help icon is shown`() {
        composeTestRule.setContent {
            HomeAssistantAppTheme {
                SettingsWearTopAppBar(
                    title = { Text("Favorites") },
                    onBackClicked = {},
                    docsLink = null,
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Get help").assertDoesNotExist()
    }

    @Test
    fun `Given a wear settings screen when it renders with a docs link then a help icon is shown`() {
        composeTestRule.setContent {
            HomeAssistantAppTheme {
                SettingsWearTopAppBar(
                    title = { Text("Favorites") },
                    onBackClicked = {},
                    docsLink = WEAR_DOCS_LINK,
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Get help").assertExists()
    }

    @Test
    fun `Given a wear settings screen when the back icon is tapped then onBackClicked is invoked`() {
        var backClicked = false

        composeTestRule.setContent {
            HomeAssistantAppTheme {
                SettingsWearTopAppBar(
                    title = { Text("Favorites") },
                    onBackClicked = { backClicked = true },
                    docsLink = null,
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Navigate up").performClick()

        assert(backClicked) { "Expected onBackClicked to be invoked after tapping the back icon" }
    }

    @Test
    fun `Given a wear settings screen when it renders then the title is displayed`() {
        composeTestRule.setContent {
            HomeAssistantAppTheme {
                SettingsWearTopAppBar(
                    title = { Text("Favorites") },
                    onBackClicked = {},
                    docsLink = null,
                )
            }
        }

        composeTestRule.onNodeWithText("Favorites").assertExists()
    }
}
