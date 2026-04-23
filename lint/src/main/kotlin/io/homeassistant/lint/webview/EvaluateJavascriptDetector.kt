package io.homeassistant.lint.webview

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.psi.KtAnnotatedExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.toUElement

private const val EVALUATE_JAVASCRIPT_METHOD = "evaluateJavascript"
private const val WEBVIEW_FQN = "android.webkit.WebView"
private const val EVALUATE_JAVASCRIPT_USAGE_FQN =
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
         * Walks up from [node] through enclosing expressions, methods and classes, returning `true`
         * if any of them carry `@EvaluateJavascriptUsage` or `@OptIn(EvaluateJavascriptUsage::class)`.
         *
         * Expression-level annotations (e.g. `@OptIn(...) evaluateJavascript(...)`) are not
         * exposed by UAST's `uAnnotations`, so we fall back to the Kotlin PSI tree for those.
         */
        private fun hasOptIn(node: UCallExpression): Boolean {
            if (hasExpressionLevelOptIn(node.sourcePsi)) return true

            val enclosingMethod = node.getParentOfType<UMethod>()
            if (enclosingMethod != null && hasEvaluateJavascriptAnnotation(enclosingMethod.uAnnotations)) {
                return true
            }

            val enclosingClass = node.getParentOfType<UClass>()
            return enclosingClass != null && hasEvaluateJavascriptAnnotation(enclosingClass.uAnnotations)
        }

        /**
         * Walks up the Kotlin PSI tree from [psi] looking for [KtAnnotatedExpression] nodes
         * that carry the opt-in annotation. Stops at function or class boundaries.
         */
        private fun hasExpressionLevelOptIn(psi: PsiElement?): Boolean {
            var current = psi?.parent
            while (current != null && current !is KtFunction && current !is KtClassOrObject) {
                if (current is KtAnnotatedExpression) {
                    val uAnnotations = current.annotationEntries.mapNotNull { it.toUElement() as? UAnnotation }
                    if (hasEvaluateJavascriptAnnotation(uAnnotations)) return true
                }
                current = current.parent
            }
            return false
        }

        private fun hasEvaluateJavascriptAnnotation(annotations: List<UAnnotation>): Boolean {
            return annotations.any { annotation ->
                val fqn = annotation.qualifiedName
                fqn == EVALUATE_JAVASCRIPT_USAGE_FQN ||
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
                    .any { it.type?.canonicalText == EVALUATE_JAVASCRIPT_USAGE_FQN }
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
