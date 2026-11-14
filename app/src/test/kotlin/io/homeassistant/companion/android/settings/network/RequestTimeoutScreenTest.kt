package io.homeassistant.companion.android.settings.network

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.compose.theme.HATheme
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w600dp-h2000dp")
@HiltAndroidTest
class RequestTimeoutScreenTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Test
    fun `Given a selected timeout when another option is clicked then its duration is returned`() {
        var selected: Duration? = null
        composeTestRule.setContent {
            HATheme {
                RequestTimeoutContent(RequestTimeoutUiState(timeout = 30.seconds), onSelect = { selected = it }, onRetry = {})
            }
        }

        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.network_request_timeout_seconds, 30L))
            .assertIsSelected()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.network_request_timeout_seconds, 10L))
            .performClick()

        assertEquals(10.seconds, selected)
    }

    @Test
    fun `Given a save in progress when an option is clicked then no second save is requested`() {
        var selected: Duration? = null
        composeTestRule.setContent {
            HATheme {
                RequestTimeoutContent(
                    RequestTimeoutUiState(timeout = 30.seconds, isSaving = true),
                    onSelect = { selected = it },
                    onRetry = {},
                )
            }
        }

        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.network_request_timeout_seconds, 5L))
            .performClick()

        assertNull(selected)
    }
}
