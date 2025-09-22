package io.homeassistant.companion.android.onboarding.localfirst

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
class LocalFirstScreenTest {
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
    fun `Given empty screen when interacting with it then invoke proper callback`() {
        composeTestRule.apply {
            var nextClicked = false
            setContent {
                LocalFirstScreen(
                    onNextClick = { nextClicked = true },
                )
            }

            onNodeWithText(stringResource(R.string.local_first_title)).assertIsDisplayed()
            onNodeWithText(stringResource(R.string.local_first_content)).assertIsDisplayed()
            onNodeWithText(stringResource(R.string.local_first_next)).performScrollTo().assertIsDisplayed().performClick()
            assertTrue(nextClicked)
        }
    }
}
