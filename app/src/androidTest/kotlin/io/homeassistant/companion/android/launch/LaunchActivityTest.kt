package io.homeassistant.companion.android.launch

import android.os.Build
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import leakcanary.DetectLeaksAfterTestSuccess
import leakcanary.LeakCanary
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import shark.AndroidReferenceMatchers

class LaunchActivityTest {
    private val composeTestRule = createAndroidComposeRule<LaunchActivity>()

    private val detectLeaksRule = DetectLeaksAfterTestSuccess()

    private val jobServiceLibraryLeakRule = object : ExternalResource() {
        private lateinit var previousConfig: LeakCanary.Config

        override fun before() {
            previousConfig = LeakCanary.config
            // WorkManager on lower APIs can retain the JobService binder stub after onDestroy(),
            // which is external to LaunchActivity and should be treated as a library leak here for
            // observed and confirmed leaks (API 23 only).
            LeakCanary.config = previousConfig.copy(
                referenceMatchers = previousConfig.referenceMatchers +
                    AndroidReferenceMatchers.nativeGlobalVariableLeak(
                        className = "android.app.job.JobService\$1",
                        description = "API 23 can retain JobService binder stubs after Service.onDestroy().",
                        patternApplies = { sdkInt < Build.VERSION_CODES.N },
                    ),
            )
        }

        override fun after() {
            LeakCanary.config = previousConfig
        }
    }

    @get:Rule
    val ruleChain: TestRule = RuleChain
        .outerRule(jobServiceLibraryLeakRule)
        .around(detectLeaksRule)
        .around(composeTestRule)

    @Test
    fun launchActivity() {
        composeTestRule.waitForIdle()
    }
}
