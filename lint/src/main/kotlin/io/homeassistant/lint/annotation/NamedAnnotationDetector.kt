package io.homeassistant.lint.annotation

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UAnnotation

object NamedAnnotationDetector {

    private const val NAMED_ANNOTATION_FQN = "javax.inject.Named"

    @JvmField
    val ISSUE = Issue.create(
        id = "NoNamedAnnotation",
        briefDescription = "Avoid using @Named annotation",
        explanation = """
            Using the @Named annotation for dependency injection is forbidden.
            Instead, create custom, type-safe qualifier annotations. Custom qualifiers
            prevent typos and allow the compiler to catch errors, whereas @Named uses
            string-based lookups which are prone to runtime errors.
        """.trimIndent(),
        category = Category.CORRECTNESS,
        priority = 10,
        severity = Severity.ERROR,
        implementation = Implementation(
            IssueDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        ),
    )

    class IssueDetector :
        Detector(),
        SourceCodeScanner {
        override fun getApplicableUastTypes() = listOf(UAnnotation::class.java)

        override fun createUastHandler(context: JavaContext): UElementHandler {
            return object : UElementHandler() {
                override fun visitAnnotation(node: UAnnotation) {
                    if (node.qualifiedName == NAMED_ANNOTATION_FQN) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Usage of @Named is forbidden. Use a custom qualifier annotation instead.",
                        )
                    }
                }
            }
        }
    }
}
