package io.homeassistant.companion.android.settings.server

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.compose.theme.HATheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class ServerChooserTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    private val items = listOf(
        ServerChooserItem(serverId = 1, userName = "Alice Smith", serverName = "Home", isActive = true),
        ServerChooserItem(serverId = 2, userName = "Bob", serverName = "Friends home"),
    )

    @Test
    fun `Given server chooser content is hosted by another sheet then it does not create a nested Compose dialog`() {
        composeTestRule.apply {
            setContent {
                HATheme {
                    ServerChooserContent(
                        items = items,
                        onServerSelected = {},
                    )
                }
            }

            onNodeWithText("Home").assertIsDisplayed()
            onNodeWithText("Friends home").assertIsDisplayed()
            onAllNodes(isDialog()).assertCountEquals(0)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun `Given server chooser is hosted directly then a modal dialog layer is created`() {
        composeTestRule.apply {
            setContent {
                HATheme {
                    ServerChooser(
                        items = items,
                        onServerSelected = {},
                    )
                }
            }

            onAllNodes(isDialog()).assertCountEquals(1)
        }
    }
}
