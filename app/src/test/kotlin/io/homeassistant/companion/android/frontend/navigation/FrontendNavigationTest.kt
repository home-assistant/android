package io.homeassistant.companion.android.frontend.navigation

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.testing.unit.ConsoleLogRule
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class FrontendNavigationTest {

    @get:Rule(order = 0)
    var consoleLog = ConsoleLogRule()

    @get:Rule(order = 1)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Test
    fun `Given NavigateToSettings event then onNavigateToSettings is called`() {
        var settingsNavigated = false
        val navigationEvents = MutableSharedFlow<FrontendNavigationEvent>()

        composeTestRule.setContent {
            FrontendNavigationHandler(
                navigationEvents = navigationEvents,
                onNavigateToSettings = { settingsNavigated = true },
            )
        }

        composeTestRule.waitForIdle()
        navigationEvents.tryEmit(FrontendNavigationEvent.NavigateToSettings)
        composeTestRule.waitForIdle()

        assertEquals(true, settingsNavigated)
    }
}
