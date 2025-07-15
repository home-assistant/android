package io.homeassistant.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue
import io.homeassistant.lint.room.CoroutineDaoFunctionsIssue
import io.homeassistant.lint.serialization.MissingSerializableAnnotationIssue

class LintRegistry : IssueRegistry() {
    override val issues: List<Issue> = listOf(
        MissingSerializableAnnotationIssue.ISSUE,
        MissingSerializableAnnotationIssue.RECOMMENDATION,
        CoroutineDaoFunctionsIssue.ISSUE,
    )

    override val api: Int = CURRENT_API

    override val vendor: Vendor = Vendor(
        vendorName = "Home Assistant",
        feedbackUrl = "https://github.com/home-assistant/android/issues",
        contact = "https://github.com/home-assistant/android/",
    )
}
