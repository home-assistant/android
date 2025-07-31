package io.homeassistant.companion.android.common.util

import android.os.Build
import android.os.StrictMode
import android.os.strictmode.Violation
import androidx.annotation.RequiresApi
import java.util.concurrent.Executors
import timber.log.Timber

/**
 * Interface for rules that can be used to ignore specific StrictMode violations.
 * This allows to skip well known violations that we cannot do anything about.
 */
interface IgnoreViolationRule {
    fun shouldIgnore(violation: Violation): Boolean
}

object HAStrictMode {
    /**
     * This method enable strict mode (VM and Thread policies) with a custom configuration that is
     * specific to the Home Assistant application.
     *
     * The thread policy is configured to detect all potential issues and log them.
     *
     * If a violation is detected by the VM policy or the thread policy, the application will [FailFast] by default.
     * However, you can provide a list of [IgnoreViolationRule] instances to ignore specific violations.
     *
     * @see android.os.StrictMode
     */
    @RequiresApi(Build.VERSION_CODES.S)
    fun enable(
        vmPolicyIgnoredViolationRules: List<IgnoreViolationRule> = emptyList(),
        threadPolicyIgnoredViolationRules: List<IgnoreViolationRule> = emptyList(),
    ) {
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectIncorrectContextUse()
                .detectUnsafeIntentLaunch()
                .detectLeakedRegistrationObjects()
                .penaltyListener(Executors.newSingleThreadExecutor()) { violation ->
                    if (!vmPolicyIgnoredViolationRules.any { it.shouldIgnore(violation) }) {
                        FailFast.failWith(violation)
                    } else {
                        Timber.w(violation, "Ignoring unexpected violation ($violation)")
                    }
                }
                .build(),
        )

        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyListener(Executors.newSingleThreadExecutor()) { violation ->
                    if (!threadPolicyIgnoredViolationRules.any { it.shouldIgnore(violation) }) {
                        FailFast.failWith(violation)
                    } else {
                        Timber.w(violation, "Ignoring unexpected violation ($violation)")
                    }
                }
                .build(),
        )
    }
}
