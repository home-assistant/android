package io.homeassistant.companion.android.frontend.error

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
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.connectivity.ConnectivityCheckResult
import io.homeassistant.companion.android.common.data.connectivity.ConnectivityCheckState
import io.homeassistant.companion.android.onboarding.connection.ConnectionErrorScreen
import io.homeassistant.companion.android.onboarding.connection.ConnectionViewModel
import io.homeassistant.companion.android.testing.unit.ConsoleLogRule
import io.homeassistant.companion.android.testing.unit.stringResource
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class FrontendConnectionErrorScreenTest {
    @get:Rule(order = 0)
    var consoleLog = ConsoleLogRule()

    @get:Rule(order = 1)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Test
    fun `Given FrontendConnectionErrorScreen when error is null then nothing is displayed`() {
        composeTestRule.apply {
            setContent {
                FrontendConnectionErrorScreen(
                    error = null,
                    url = null,
                    onOpenExternalLink = {},
                    connectivityCheckState = ConnectivityCheckState(),
                )
            }
            onRoot().assertIsNotDisplayed()
        }
    }

    @Test
    fun `Given FrontendConnectionErrorScreen when error is not null and url is null then url info is not displayed but the error is`() {
        composeTestRule.apply {
            var urlClicked: String? = null
            setContent {
                FrontendConnectionErrorScreen(
                    error = FrontendConnectionError.UnknownError(R.string.tls_cert_expired_message, "details", "errorType"),
                    url = null,
                    onOpenExternalLink = {
                        urlClicked = it.toString()
                    },
                    connectivityCheckState = ConnectivityCheckState(),
                )
            }

            onNodeWithText(stringResource(R.string.error_connection_failed)).assertIsDisplayed()
            onNodeWithText(stringResource(R.string.tls_cert_expired_message)).assertIsDisplayed()
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
            assertEquals(
                "https://companion.home-assistant.io/docs/troubleshooting/faqs/",
                urlClicked,
            )

            onNodeWithContentDescription(stringResource(R.string.connection_error_forum_content_description))
                .performScrollTo().assertIsDisplayed().performClick()
            assertEquals(
                "https://community.home-assistant.io/c/mobile-apps/android-companion/42",
                urlClicked,
            )

            onNodeWithContentDescription(stringResource(R.string.connection_error_github_content_description))
                .performScrollTo().assertIsDisplayed().performClick()
            assertEquals("https://github.com/home-assistant/android/issues", urlClicked)

            onNodeWithContentDescription(stringResource(R.string.connection_error_discord_content_description))
                .performScrollTo().assertIsDisplayed().performClick()
            assertEquals(
                "https://discord.com/channels/330944238910963714/1284965926336335993",
                urlClicked,
            )
        }
    }

    @Test
    fun `Given FrontendConnectionErrorScreen when error and url are not null then error and url info are displayed`() {
        composeTestRule.apply {
            val url = "http://ha.org"
            setContent {
                FrontendConnectionErrorScreen(
                    error = FrontendConnectionError.AuthenticationError(R.string.tls_cert_expired_message, "details", "errorType"),
                    url = url,
                    onOpenExternalLink = {},
                    connectivityCheckState = ConnectivityCheckState(),
                )
            }

            onNodeWithText(stringResource(R.string.error_connection_failed)).assertIsDisplayed()
            onNodeWithText(stringResource(R.string.tls_cert_expired_message)).assertIsDisplayed()
            onNodeWithTag(URL_INFO_TAG).assertIsDisplayed()
            onNodeWithText("${stringResource(R.string.connection_error_url_info)}\n$url").assertIsDisplayed()
        }
    }

    @Test
    fun `Given FrontendConnectionErrorScreen with viewmodel when retry is clicked then connectivity check is retried`() {
        val viewModel = mockk<ConnectionViewModel>()
        val error = FrontendConnectionError.UnreachableError(
            R.string.tls_cert_expired_message,
            "details",
            "errorType",
        )
        every { viewModel.urlFlow } returns MutableStateFlow("http://ha.org")
        every { viewModel.errorFlow } returns MutableStateFlow<FrontendConnectionError?>(error)
        every { viewModel.connectivityCheckState } returns MutableStateFlow(
            ConnectivityCheckState(
                dnsResolution = ConnectivityCheckResult.Success(
                    R.string.connection_check_dns,
                    "1.1.1.1",
                ),
                portReachability = ConnectivityCheckResult.Success(
                    R.string.connection_check_port,
                    "80",
                ),
                tlsCertificate = ConnectivityCheckResult.NotApplicable(
                    R.string.connection_check_tls_not_applicable,
                ),
                serverConnection = ConnectivityCheckResult.Success(
                    R.string.connection_check_server,
                    "ok",
                ),
                homeAssistantVerification = ConnectivityCheckResult.Success(
                    R.string.connection_check_home_assistant,
                    "ok",
                ),
            ),
        )
        every { viewModel.runConnectivityChecks() } just Runs

        composeTestRule.apply {
            setContent {
                ConnectionErrorScreen(
                    stateProvider = viewModel,
                    onOpenExternalLink = {},
                    onCloseClick = {},
                )
            }

            onNodeWithText(stringResource(R.string.connection_error_more_details))
                .performScrollTo()
                .performClick()

            onNodeWithText(stringResource(R.string.retry))
                .performScrollTo()
                .performClick()

            runOnIdle {
                verify(exactly = 1) { viewModel.runConnectivityChecks() }
            }
        }
    }
}
