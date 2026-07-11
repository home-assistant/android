package io.homeassistant.companion.android.frontend

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class FrontendVisibleLifecycleEffectTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Test
    fun `Given the frontend shown when the host activity stops and starts then visibility follows`() {
        val published = mutableListOf<Boolean>()
        val setFrontendVisible: (Boolean) -> Unit = { published.add(it) }

        composeTestRule.setContent {
            FrontendVisibleLifecycleEffect(setFrontendVisible)
        }
        composeTestRule.waitForIdle()
        assertEquals(listOf(true), published)

        // Screen off / app backgrounded dispatches ON_STOP.
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        assertEquals(listOf(true, false), published)

        // Returning to the foreground dispatches ON_START.
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        assertEquals(listOf(true, false, true), published)
    }
}
