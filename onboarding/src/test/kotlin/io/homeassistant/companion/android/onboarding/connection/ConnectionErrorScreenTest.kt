package io.homeassistant.companion.android.onboarding.connection

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.onboarding.R
import io.homeassistant.companion.android.testing.unit.ConsoleLogTree
import io.homeassistant.companion.android.testing.unit.stringResource
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timber.log.Timber

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class ConnectionErrorScreenTest {
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
    fun `Given ConnectionErrorScreen when error is null then nothing is displayed`() {
        composeTestRule.apply {
            setContent {
                ConnectionErrorScreen(
                    error = null,
                    url = null,
                    onBackClick = {},
                    onOpenExternalLink = {},
                )
            }
            onRoot().assertIsNotDisplayed()
        }
    }

    @Test
    fun `Given ConnectionErrorScreen when error is not null and url is null then url info is not displayed but the error is`() {
        composeTestRule.apply {
            var urlClicked: String? = null
            var onBackClicked: Boolean = false
            setContent {
                ConnectionErrorScreen(
                    error = ConnectionError.UnknownError(commonR.string.tls_cert_expired_message, "details", "errorType"),
                    url = null,
                    onBackClick = {
                        onBackClicked = true
                    },
                    onOpenExternalLink = {
                        urlClicked = it.toString()
                    },
                )
            }

            onNodeWithText(stringResource(commonR.string.error_connection_failed)).assertIsDisplayed()
            onNodeWithText(stringResource(commonR.string.tls_cert_expired_message)).assertIsDisplayed()
            onNodeWithTag(URL_INFO_TAG).assertIsNotDisplayed()

            val description = onNodeWithText(stringResource(R.string.connection_error_more_details_description))
            val errorDetail = onNodeWithText(stringResource(R.string.connection_error_more_details_error))
            description.assertIsNotDisplayed()
            errorDetail.assertIsNotDisplayed()

            onNodeWithText(stringResource(R.string.connection_error_more_details)).assertIsDisplayed().performClick()
            description.assertIsDisplayed()
            errorDetail.assertIsDisplayed()

            onNodeWithText(stringResource(R.string.connection_error_help)).performScrollTo().assertIsDisplayed()

            onNodeWithContentDescription(stringResource(R.string.connection_error_documentation_content_description))
                .performScrollTo().assertIsDisplayed().performClick()
            assertEquals("https://companion.home-assistant.io/docs/troubleshooting/faqs/", urlClicked)

            onNodeWithContentDescription("Home Assistant Github")
                .performScrollTo().assertIsDisplayed().performClick()
            assertEquals("https://github.com/home-assistant/android/", urlClicked)

            onNodeWithContentDescription("Home Assistant Discord")
                .performScrollTo().assertIsDisplayed().performClick()
            assertEquals("https://discord.com/channels/330944238910963714/1284965926336335993", urlClicked)

            onNodeWithText(stringResource(R.string.back)).performScrollTo().assertIsDisplayed().performClick()
            assertTrue(onBackClicked)
        }
    }

    @Test
    fun `Given ConnectionErrorScreen when error and url are not null then error and url info are displayed`() {
        composeTestRule.apply {
            val url = "http://ha.org"
            setContent {
                ConnectionErrorScreen(
                    error = ConnectionError.AuthenticationError(commonR.string.tls_cert_expired_message, "details", "errorType"),
                    url = url,
                    onBackClick = {},
                    onOpenExternalLink = {},
                )
            }

            onNodeWithText(stringResource(commonR.string.error_connection_failed)).assertIsDisplayed()
            onNodeWithText(stringResource(commonR.string.tls_cert_expired_message)).assertIsDisplayed()
            onNodeWithTag(URL_INFO_TAG).assertIsDisplayed()
            onNodeWithText("${stringResource(R.string.connection_error_url_info)}\n$url").assertIsDisplayed()
        }
    }
}
