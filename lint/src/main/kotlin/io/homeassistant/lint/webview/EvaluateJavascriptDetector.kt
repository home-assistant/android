package io.homeassistant.lint.webview

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getParentOfType

private const val EVALUATE_JAVASCRIPT_METHOD = "evaluateJavascript"
private const val WEBVIEW_FQN = "android.webkit.WebView"
private const val EVALUATE_SCRIPT_USAGE_FQN =
    "io.homeassistant.companion.android.frontend.EvaluateJavascriptUsage"
private const val KOTLIN_OPT_IN_FQN = "kotlin.OptIn"
private const val ANDROIDX_OPT_IN_FQN = "androidx.annotation.OptIn"

object EvaluateJavascriptDetector {

    @JvmField
    val ISSUE = Issue.create(
        id = "EvaluateJavascriptUsage",
        briefDescription = "Direct usage of WebView.evaluateJavascript requires opt-in",
        explanation = """
            Evaluating raw JavaScript tightly couples the app to frontend internals and is \
            fragile across frontend changes. Prefer collaborating with the frontend team to \
            add a dedicated externalBus message. Only opt in as a last resort, and document \
            on the opt-in site why the externalBus is not a viable option so reviewers can \
            challenge the usage.
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

        override fun getApplicableMethodNames(): List<String> = listOf(EVALUATE_JAVASCRIPT_METHOD)

        override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
            if (!context.evaluator.isMemberInClass(method, WEBVIEW_FQN)) return

            if (hasOptIn(node)) return

            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Usage of `WebView.evaluateJavascript` requires `@EvaluateJavascriptUsage` " +
                    "or `@OptIn(EvaluateJavascriptUsage::class)` on the enclosing function or class.",
            )
        }

        /**
         * Walks up from [node] through enclosing methods and classes, returning `true`
         * if any of them carry `@EvaluateJavascriptUsage` or `@OptIn(EvaluateJavascriptUsage::class)`.
         */
        private fun hasOptIn(node: UCallExpression): Boolean {
            val enclosingMethod = node.getParentOfType<UMethod>()
            if (enclosingMethod != null && hasEvaluateJavascriptAnnotation(enclosingMethod.uAnnotations)) {
                return true
            }

            val enclosingClass = node.getParentOfType<UClass>()
            return enclosingClass != null && hasEvaluateJavascriptAnnotation(enclosingClass.uAnnotations)
        }

        private fun hasEvaluateJavascriptAnnotation(annotations: List<UAnnotation>): Boolean {
            return annotations.any { annotation ->
                val fqn = annotation.qualifiedName
                fqn == EVALUATE_SCRIPT_USAGE_FQN ||
                    (
                        (fqn == KOTLIN_OPT_IN_FQN || fqn == ANDROIDX_OPT_IN_FQN) &&
                            referencesEvaluateJavascript(annotation)
                        )
            }
        }

        /**
         * Checks whether an `@OptIn(...)` annotation includes `EvaluateJavascriptUsage::class`
         * among its arguments by resolving the class literal types.
         */
        private fun referencesEvaluateJavascript(optInAnnotation: UAnnotation): Boolean {
            return optInAnnotation.attributeValues.any { attribute ->
                collectClassLiterals(attribute.expression)
                    .any { it.type?.canonicalText == EVALUATE_SCRIPT_USAGE_FQN }
            }
        }

        private fun collectClassLiterals(expression: UExpression): List<UClassLiteralExpression> {
            return when (expression) {
                is UClassLiteralExpression -> listOf(expression)
                // vararg wraps arguments in an array initializer expression
                is UCallExpression -> expression.valueArguments.filterIsInstance<UClassLiteralExpression>()
                else -> emptyList()
            }
        }
    }
}
