package io.homeassistant.lint.sdkversion

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import org.jetbrains.uast.UReferenceExpression

private const val SDK_INT_FIELD_NAME = "SDK_INT"
private const val BUILD_VERSION_FQN = "android.os.Build.VERSION"

object SdkVersionDetector {

    @JvmField
    val ISSUE = Issue.create(
        id = "SdkVersionAccess",
        briefDescription = "Direct read of Build.VERSION.SDK_INT bypasses the SdkVersion object",
        explanation = """
            Code should call `SdkVersion.isAtLeast(version)` instead of reading
            `Build.VERSION.SDK_INT` directly for version gates. `SdkVersion` exposes a
            `@VisibleForTesting` `sdkInt` property so tests can simulate other SDK levels, and its
            `@ChecksSdkIntAtLeast(parameter = 0)` annotation lets Android Lint recognise the
            version gate without `@SuppressLint("NewApi")`.

            When you need the raw `SDK_INT` value as a string — typically for diagnostic
            logging or registration payloads — use `SdkVersion.toString()` (or string
            interpolation `"... ${"$"}SdkVersion ..."`) instead of `Build.VERSION.SDK_INT.toString()`.

            If a call site genuinely needs to read `Build.VERSION.SDK_INT` directly (typically
            the `SdkVersion` object itself), suppress with
            `@SuppressLint("SdkVersionAccess")` on the enclosing function, property, or class
            and document why in code review.
        """.trimIndent(),
        category = Category.CORRECTNESS,
        priority = 8,
        severity = Severity.ERROR,
        implementation = Implementation(
            IssueDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        ),
    )

    class IssueDetector :
        Detector(),
        SourceCodeScanner {

        override fun getApplicableReferenceNames(): List<String> = listOf(SDK_INT_FIELD_NAME)

        override fun visitReference(context: JavaContext, reference: UReferenceExpression, referenced: PsiElement) {
            if (referenced !is PsiField) return
            val containingClass = referenced.containingClass ?: return
            if (containingClass.qualifiedName != BUILD_VERSION_FQN) return

            context.report(
                ISSUE,
                reference,
                context.getLocation(reference),
                "Read of `Build.VERSION.SDK_INT` is forbidden. Use `SdkVersion.isAtLeast(version)` " +
                    "for version gates, or `SdkVersion.toString()` / `\"... \$SdkVersion ...\"` " +
                    "when you need the raw value as a string. Suppress with " +
                    "`@SuppressLint(\"SdkVersionAccess\")` only if this site is one of the few " +
                    "legitimate raw accesses.",
            )
        }
    }
}
