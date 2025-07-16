package io.homeassistant.lint.room

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.kotlin.getKotlinMemberOrigin

object CoroutineDaoFunctionsIssue {

    @JvmField
    val ISSUE = Issue.Companion.create(
        id = "CoroutineDaoFunction",
        briefDescription = "DAO functions should suspend or return a Flow",
        explanation = """All functions in a DAO should suspend or return a Flow to ensure 
            |they can be executed properly in coroutines
        """.trimMargin(),
        category = Category.Companion.CORRECTNESS,
        severity = Severity.ERROR,
        priority = 10,
        implementation = Implementation(
            IssueDetector::class.java,
            Scope.Companion.JAVA_FILE_SCOPE,
        ),
    )

    class IssueDetector :
        Detector(),
        SourceCodeScanner {
        override fun getApplicableUastTypes() = listOf(UClass::class.java)

        override fun createUastHandler(context: JavaContext): UElementHandler {
            return object : UElementHandler() {
                override fun visitClass(node: UClass) {
                    if (node.isDao()) {
                        node.methods.forEach { method ->
                            checkMethod(context, method)
                        }
                    }
                }
            }
        }

        private fun checkMethod(context: JavaContext, method: UMethod) {
            if (!method.isSuspend() && !method.isReturningFlow()) {
                context.report(
                    ISSUE,
                    method,
                    context.getLocation(method),
                    "DAO functions should suspend or return a Flow.",
                )
            }
        }
    }
}

private fun UClass.isDao(): Boolean {
    return hasAnnotation("androidx.room.Dao")
}

private fun UMethod.isSuspend(): Boolean {
    return getKotlinMemberOrigin(javaPsi.originalElement)?.hasModifier(KtTokens.SUSPEND_KEYWORD) == true
}

private fun UMethod.isReturningFlow(): Boolean {
    return returnType?.canonicalText?.startsWith("kotlinx.coroutines.flow.Flow") == true
}
