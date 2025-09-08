package io.homeassistant.companion.android.onboarding.connection

import android.webkit.WebViewClient
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.onboarding.R
import io.homeassistant.companion.android.testing.unit.ConsoleLogTree
import io.homeassistant.companion.android.testing.unit.stringResource
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timber.log.Timber

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class ConnectionScreenTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Before
    fun setup() {
        Timber.plant(ConsoleLogTree)
        ConsoleLogTree.verbose = true
    }

    @Test
    fun `Given ConnectionScreen when isLoading then show loading`() {
        composeTestRule.apply {
            setContent {
                ConnectionScreen(
                    onBackPressed = {},
                    isLoading = true,
                    url = "",
                    webViewClient = WebViewClient(),
                )
            }
            onNodeWithContentDescription(stringResource(R.string.loading_content_description)).assertIsDisplayed()
        }
    }

    @Test
    fun `Given ConnectionScreen when not isLoading then don't show loading`() {
        composeTestRule.apply {
            setContent {
                ConnectionScreen(
                    onBackPressed = {},
                    isLoading = false,
                    url = "",
                    webViewClient = WebViewClient(),
                )
            }
            onNodeWithContentDescription(stringResource(R.string.loading_content_description)).assertIsNotDisplayed()
        }
    }

    @Test
    fun `Given ConnectionScreen when pressing back then triggers onBackPressed`() {
        var backPressed = false
        composeTestRule.apply {
            setContent {
                ConnectionScreen(
                    onBackPressed = {
                        backPressed = true
                    },
                    isLoading = false,
                    url = "",
                    webViewClient = WebViewClient(),
                )
            }

            activity.onBackPressedDispatcher.onBackPressed()

            assertTrue(backPressed)
        }
    }
}
