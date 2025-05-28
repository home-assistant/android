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
import io.homeassistant.lint.serialization.MissingSerializableAnnotationIssue.RECOMMENDATION
import io.homeassistant.lint.utils.Logger
import kotlin.reflect.jvm.jvmName
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression

private const val KOTLINX_JSON_CLASS = "kotlinx.serialization.json.Json"
private const val KOTLINX_SERIALIZABLE_ANNOTATION = "kotlinx.serialization.Serializable"
private const val ENCODE_TO_STRING_METHOD_NAME = "encodeToString"
private const val DECODE_FROM_STRING_METHOD_NAME = "decodeFromString"
private const val VALUE_PARAM_NAME = "value"
private const val STRING_PARAM_NAME = "string"
private const val SERIALIZER_PARAM_NAME = "serializer"
private const val DESERIALIZER_PARAM_NAME = "deserializer"
private val WELL_KNOWN_SERIALIZABLE_TYPES = listOf<String>(
    "java.lang.Boolean",
    "java.lang.Integer",
    "java.lang.Long",
    "java.lang.Number",
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
                    if (node.receiver?.getExpressionType()?.canonicalText == KOTLINX_JSON_CLASS) {
                        val argument = when (node.methodIdentifier?.name) {
                            ENCODE_TO_STRING_METHOD_NAME -> {
                                val argument = EncodeToStringVisitor.getValueArgumentToCheck(context, node) ?: return

                                val argumentType = argument.getExpressionType()

                                if (argumentType == null) {
                                    Logger.warn("Missing argument type in $ENCODE_TO_STRING_METHOD_NAME call, check $ISSUE_CLASS_NAME code.")
                                    return
                                }
                                argumentType
                            }
                            DECODE_FROM_STRING_METHOD_NAME -> DecodeFromStringStringVisitor.getTypeArgumentToCheck(context, node) ?: return
                            else -> return
                        }
                        inspectType(node, argument)
                    }
                }

                private fun inspectType(node: UCallExpression, type: PsiType) {
                    collectAllTypes(type)
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

private object EncodeToStringVisitor {
    fun getValueArgumentToCheck(context: JavaContext, node: UCallExpression): UExpression? {
        return if (node.valueArgumentCount == 1) {
            node.valueArguments.first()
        } else {
            getValueArgumentFromMultipleArgument(context, node)
        }
    }

    private fun getValueArgumentFromMultipleArgument(context: JavaContext, node: UCallExpression): UExpression? {
        val resolvedMethod = node.resolve()

        if (resolvedMethod == null) {
            Logger.warn("Could not resolved method call $ENCODE_TO_STRING_METHOD_NAME, check $ISSUE_CLASS_NAME code.")
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
                else -> Logger.warn("$ISSUE_CLASS_NAME is outdated for $ENCODE_TO_STRING_METHOD_NAME please open an issue or update the lint rule.")
            }
        }

        if (valueIndexParam == null || serializerIndexParam == null) {
            Logger.warn("Missing parameter in $ENCODE_TO_STRING_METHOD_NAME call, check $ISSUE_CLASS_NAME code.")
            return null
        }

        if (isUsingAnySerializer(context, node, serializerIndexParam)) {
            // We ignore the rule if we find a *AnySerializer
            return null
        }

        val argument = node.getArgumentForParameter(valueIndexParam)
        if (argument == null) {
            Logger.warn("Fail to get argument value in $ENCODE_TO_STRING_METHOD_NAME call, check $ISSUE_CLASS_NAME code.")
            return null
        }
        return argument
    }
}

private object DecodeFromStringStringVisitor {
    fun getTypeArgumentToCheck(context: JavaContext, node: UCallExpression): PsiType? {
        if (node.valueArgumentCount != 1) {
            if (shouldSkipTypeArgumentCheck(context, node)) {
                // We ignore the rule if we find a *AnySerializer
                return null
            }
        }
        // TODO handle the case where the typeArguement is deduced from the receiver variable
        return node.returnType
//        val typeArguments = node.typeArguments
//        if (typeArguments.size != 1) {
//            Logger.warn("Could not resolved type argument call of $DECODE_FROM_STRING_METHOD_NAME, check $ISSUE_CLASS_NAME code.")
//            return null
//        }
//        return typeArguments.first()
    }

    private fun shouldSkipTypeArgumentCheck(context: JavaContext, node: UCallExpression): Boolean {
        val resolvedMethod = node.resolve()

        if (resolvedMethod == null) {
            Logger.warn("Could not resolved method call $DECODE_FROM_STRING_METHOD_NAME, check $ISSUE_CLASS_NAME code.")
            return false
        }

        val parameters = resolvedMethod.parameterList.parameters

        var deserializerIndexParam: Int? = null

        parameters.forEachIndexed { index, value ->
            when (value.name) {
                STRING_PARAM_NAME -> {} // no-op
                DESERIALIZER_PARAM_NAME -> deserializerIndexParam = index
                else -> Logger.warn("$ISSUE_CLASS_NAME is outdated for $DECODE_FROM_STRING_METHOD_NAME please open an issue or update the lint rule.")
            }
        }

        if (deserializerIndexParam == null) {
            Logger.warn("Missing deserializer parameter in $DECODE_FROM_STRING_METHOD_NAME call, check $ISSUE_CLASS_NAME code.")
            return false
        }

        return isUsingAnySerializer(context, node, deserializerIndexParam)
    }
}

private fun isUsingAnySerializer(context: JavaContext, node: UCallExpression, serializerIndexParam: Int): Boolean {
    val serializerArg = node.getArgumentForParameter(serializerIndexParam)
    val serializerClass = context.evaluator.getTypeClass(serializerArg?.getExpressionType())
    if (serializerClass?.qualifiedName in ANY_SERIALIZER_TYPES) {
        context.report(
            RECOMMENDATION,
            node,
            context.getLocation(serializerArg),
            "Prefer polymorphic serializer over AnySerializer.",
        )
        return true
    }
    return false
}
