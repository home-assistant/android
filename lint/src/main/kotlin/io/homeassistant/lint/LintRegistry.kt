package io.homeassistant.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue
import io.homeassistant.lint.serialization.MissingSerializableAnnotationIssue

class LintRegistry : IssueRegistry() {
    override val issues: List<Issue> = listOf<Issue>(
        MissingSerializableAnnotationIssue.ISSUE,
        MissingSerializableAnnotationIssue.RECOMMENDATION,
    )

    override val api: Int = CURRENT_API

    override val vendor: Vendor = Vendor(
        vendorName = "Home assistant",
        feedbackUrl = "https://github.com/home-assistant/android/issues",
        contact = "https://github.com/home-assistant/android/",
    )
}
