package io.homeassistant.companion.android.launch

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import leakcanary.DetectLeaksAfterTestSuccess
import leakcanary.LeakCanary
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement
import shark.AndroidReferenceMatchers

class LaunchActivityTest {
    private val composeTestRule = createAndroidComposeRule<LaunchActivity>()

    private val detectLeaksRule = DetectLeaksAfterTestSuccess()

    private val jobServiceLibraryLeakRule: TestRule = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                val previousConfig = LeakCanary.config
                LeakCanary.config = previousConfig.copy(
                    // WorkManager on API 23 can retain the JobService binder stub after onDestroy(),
                    // which is external to LaunchActivity and should be treated as a library leak here.
                    referenceMatchers = previousConfig.referenceMatchers +
                        AndroidReferenceMatchers.nativeGlobalVariableLeak(
                            className = "android.app.job.JobService\$1",
                            description = "API 23 can retain JobService binder stubs after Service.onDestroy().",
                            patternApplies = { sdkInt < 24 },
                        ),
                )
                try {
                    base.evaluate()
                } finally {
                    LeakCanary.config = previousConfig
                }
            }
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
