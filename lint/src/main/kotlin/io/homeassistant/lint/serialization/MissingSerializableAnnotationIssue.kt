package io.homeassistant.lint.serialization

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiWildcardType
import io.homeassistant.lint.utils.Logger
import kotlin.reflect.jvm.jvmName
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression

private const val KOTLINX_JSON_CLASS = "kotlinx.serialization.json.Json"
private const val KOTLINX_SERIALIZABLE_ANNOTATION = "kotlinx.serialization.Serializable"
private const val METHOD_NAME = "encodeToString" // TODO handle decodeFromString as well
private const val VALUE_PARAM_NAME = "value"
private const val SERIALIZER_PARAM_NAME = "serializer"
private val WELL_KNOWN_SERIALIZABLE_TYPES = listOf<String>(
    "java.lang.Integer",
    "java.util.HashMap",
    "java.util.List",
    "java.util.Map",
    "java.lang.String",
)
private val ANY_SERIALIZER_TYPES = listOf<String>(
    "io.homeassistant.companion.android.common.util.AnySerializer",
    "io.homeassistant.companion.android.common.util.MapAnySerializer",
)
private val ISSUE_IMPLEMENTATION = Implementation(
    MissingSerializableAnnotationIssue.IssueDetector::class.java,
    Scope.JAVA_FILE_SCOPE,
)
private val ISSUE_CLASS_NAME = MissingSerializableAnnotationIssue::class.jvmName

object MissingSerializableAnnotationIssue {
    @JvmField
    val ISSUE = Issue.create(
        id = "MissingSerializableAnnotation",
        briefDescription = "@Serializable annotation is missing",
        explanation = """
                When using KotlinX Serialization the @Serializable annotation is required on classes that are serialized.
                This lint check helps to ensure that all classes being serialized have the proper annotation.
        """.trimIndent(),
        category = Category.CORRECTNESS,
        priority = 10,
        severity = Severity.ERROR,
        implementation = ISSUE_IMPLEMENTATION,
    )

    @JvmField
    val RECOMMENDATION = Issue.create(
        id = "AvoidAnySerializer",
        briefDescription = "AnySerializer is dangerous and should be used with caution",
        explanation = """
                Using `AnySerializer` with KotlinX Serialization is discouraged because it bypasses Kotlin's type checking.
                This can lead to runtime errors and make your code harder to maintain.
                Consider using polymorphic serializers with a fallback to a generic type for better type safety.
        """.trimIndent(),
        category = Category.USABILITY,
        priority = 2,
        severity = Severity.INFORMATIONAL,
        implementation = ISSUE_IMPLEMENTATION,
    )

    class IssueDetector : Detector(), Detector.UastScanner {

        override fun getApplicableUastTypes(): List<Class<out UElement>>? {
            return listOf(UCallExpression::class.java)
        }

        override fun createUastHandler(context: JavaContext): UElementHandler? {
            return object : UElementHandler() {
                override fun visitCallExpression(node: UCallExpression) {
                    if (
                        node.methodIdentifier?.name == METHOD_NAME &&
                        node.receiver?.getExpressionType()?.canonicalText == KOTLINX_JSON_CLASS
                    ) {
                        val argument = getValueArgumentToCheck(node) ?: return

                        val argumentType = argument.getExpressionType()

                        if (argumentType == null) {
                            Logger.warn("Missing argument type in $METHOD_NAME call, check $ISSUE_CLASS_NAME code.")
                            return
                        }

                        collectAllTypes(argumentType)
                            .map(context.evaluator::getTypeClass)
                            .filter {
                                it != null && it.qualifiedName !in WELL_KNOWN_SERIALIZABLE_TYPES
                            }
                            .forEach { psiClass ->
                                if (psiClass?.hasAnnotation(KOTLINX_SERIALIZABLE_ANNOTATION) == false) {
                                    context.report(
                                        ISSUE,
                                        node,
                                        context.getLocation(node),
                                        "The class `${psiClass.qualifiedName}` is missing the `@Serializable` annotation.",
                                    )
                                }
                            }
                    }
                }

                private fun getValueArgumentToCheck(node: UCallExpression): UExpression? {
                    return if (node.valueArgumentCount == 1) {
                        node.valueArguments.first()
                    } else {
                        handleMultipleArgs(node)
                    }
                }

                private fun handleMultipleArgs(node: UCallExpression): UExpression? {
                    val resolvedMethod = node.resolve()

                    if (resolvedMethod == null) {
                        Logger.warn("Could not resolved method call, check $ISSUE_CLASS_NAME code.")
                        return null
                    }

                    val parameters = resolvedMethod.parameterList.parameters

                    // We cannot assume the indexes of the param since they can change
                    var valueIndexParam: Int? = null
                    var serializerIndexParam: Int? = null

                    parameters.forEachIndexed { index, value ->
                        when (value.name) {
                            VALUE_PARAM_NAME -> valueIndexParam = index
                            SERIALIZER_PARAM_NAME -> serializerIndexParam = index
                            else -> Logger.warn("$ISSUE_CLASS_NAME is outdated please open an issue or update the lint rule.")
                        }
                    }

                    if (valueIndexParam == null || serializerIndexParam == null) {
                        Logger.warn("Missing parameter in $METHOD_NAME call, check $ISSUE_CLASS_NAME code.")
                        return null
                    }

                    val serializerArg = node.getArgumentForParameter(serializerIndexParam)
                    val serializerClass = context.evaluator.getTypeClass(serializerArg?.getExpressionType())
                    if (serializerClass?.qualifiedName in ANY_SERIALIZER_TYPES) {
                        context.report(
                            RECOMMENDATION,
                            node,
                            context.getLocation(serializerArg),
                            "Prefer polymorphic serializer over AnySerializer.",
                        )
                        // We ignore the rule if we find a *AnySerializer
                        return null
                    }

                    val argument = node.getArgumentForParameter(valueIndexParam)
                    if (argument == null) {
                        Logger.warn("Fail to get argument value in $METHOD_NAME call, check $ISSUE_CLASS_NAME code.")
                        return null
                    }
                    return argument
                }

                private fun collectAllTypes(rootType: PsiType): List<PsiType> {
                    val typesToInspect = mutableSetOf<PsiType?>()
                    fun collectTypes(type: PsiType) {
                        // In case of a Map<String, Something> we need to get the bound otherwise we cannot get the TypeClass
                        val unboundType = if (type is PsiWildcardType && type.bound != null) {
                            type.bound
                        } else {
                            type
                        }
                        typesToInspect.add(unboundType)
                        if (unboundType is PsiClassType && unboundType.parameterCount > 0) {
                            unboundType.parameters.forEach(::collectTypes)
                        }
                    }
                    collectTypes(rootType)

                    return typesToInspect.filterNotNull()
                }
            }
        }
    }
}
