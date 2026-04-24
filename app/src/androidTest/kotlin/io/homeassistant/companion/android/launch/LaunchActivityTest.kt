package io.homeassistant.companion.android.launch

import android.os.Build
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import leakcanary.DetectLeaksAfterTestSuccess
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule

class LaunchActivityTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<LaunchActivity>()

    @get:Rule
    val detectLeaksRule: TestRule = TestRule { base, description ->
        // API 23 can retain WorkManager's SystemJobService after LaunchActivity schedules background work,
        // which trips LeakCanary on a library-managed reference path rather than an activity leak.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            DetectLeaksAfterTestSuccess().apply(base, description)
        } else {
            base
        }
    }

    @Test
    fun launchActivity() {
        composeTestRule.waitForIdle()
    }
}
