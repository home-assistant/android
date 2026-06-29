package io.homeassistant.companion.android.sensors

import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.Visibility
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.KotlinPoetKspPreview
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo

private const val PROVIDES_SENSOR_FQN = "io.homeassistant.companion.android.common.sensors.ProvidesSensor"
private const val BASIC_SENSOR_FQN = "io.homeassistant.companion.android.common.sensors.SensorManager.BasicSensor"
private const val GENERATED_PACKAGE = "io.homeassistant.companion.android.sensors.generated"
private const val GENERATED_OBJECT_PREFIX = "GeneratedProvidesSensor"

private val BASIC_SENSOR =
    ClassName("io.homeassistant.companion.android.common.sensors", "SensorManager", "BasicSensor")
private val SET = ClassName("kotlin.collections", "Set")

private const val REFERENCEABILITY_ERROR =
    "@ProvidesSensor BasicSensor must be a non-private top-level, object, or companion-object val " +
        "so it can be referenced from generated code"

private const val TYPE_ERROR =
    "@ProvidesSensor can only annotate a SensorManager.BasicSensor val"

/**
 * Collects every `val` annotated with `@ProvidesSensor` and generates a plain
 * `GeneratedProvidesSensor<suffix>` data object exposing those `SensorManager.BasicSensor` values as
 * a `Set`. A hand-written source Hilt module contributes that set into the `Set<BasicSensor>`
 * multibinding — Hilt cannot reliably aggregate an `@InstallIn` module *generated* by another KSP
 * processor (the KSP2 round model does not feed it to Hilt's processor), so the Hilt binding must
 * live in source.
 *
 * The processor is a pure collector: it resolves each annotated property to a statically
 * referenceable expression (a non-private top-level, `object`, or `companion object` val). A
 * property that is not a `BasicSensor`, or that cannot be referenced from generated code (an
 * instance member or a private val), is reported as an error. Symbols that have not yet been fully
 * resolved are deferred to a later round.
 *
 * References are accumulated across rounds and the module is written exactly once in [finish]. KSP
 * only hands each symbol to [process] in the round it first resolves, so generating per round would
 * both lose earlier references and re-write the same file name on a later round.
 */
class ProvidesSensorProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val moduleSuffix: String,
) : SymbolProcessor {

    private val references = mutableListOf<CodeBlock>()
    private val originatingFiles = mutableSetOf<KSFile>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val deferred = mutableListOf<KSAnnotated>()

        for (property in resolver.getSymbolsWithAnnotation(PROVIDES_SENSOR_FQN)
            .filterIsInstance<KSPropertyDeclaration>()) {
            if (!property.validate()) {
                deferred.add(property)
                continue
            }
            if (!property.isBasicSensor()) {
                logger.error(TYPE_ERROR, property)
                continue
            }
            val reference = referenceOrNull(property)
            if (reference == null) {
                logger.error(REFERENCEABILITY_ERROR, property)
            } else {
                references.add(reference)
                property.containingFile?.let(originatingFiles::add)
            }
        }

        return deferred
    }

    override fun finish() {
        if (references.isNotEmpty()) {
            generateSet(references, originatingFiles.toTypedArray())
        }
    }

    /**
     * Whether the property's type is `SensorManager.BasicSensor`. The annotation's
     * `@Target(PROPERTY)` cannot restrict this, so annotating any other type is rejected here.
     * `BasicSensor` is a final data class, so an exact type match is sufficient.
     */
    private fun KSPropertyDeclaration.isBasicSensor(): Boolean =
        type.resolve().declaration.qualifiedName?.asString() == BASIC_SENSOR_FQN

    /** Resolves how to reference the property from generated code, or null if it isn't statically reachable. */
    @OptIn(KotlinPoetKspPreview::class)
    private fun referenceOrNull(property: KSPropertyDeclaration): CodeBlock? {
        // A private val is only visible within its own file/class, so the generated set builder (a
        // separate file) cannot reach it regardless of where it is declared.
        if (property.getVisibility() == Visibility.PRIVATE) return null

        val parent = property.parentDeclaration
        val name = property.simpleName.asString()
        return when (parent) {
            null -> CodeBlock.of("%M", MemberName(property.packageName.asString(), name))
            is KSClassDeclaration if parent.classKind == ClassKind.OBJECT ->
                CodeBlock.of("%T.%N", parent.toClassName(), name)
            // Instance property, not statically referenceable.
            else -> null
        }
    }

    @OptIn(KotlinPoetKspPreview::class)
    private fun generateSet(references: List<CodeBlock>, originatingFiles: Array<KSFile>) {
        val objectName = GENERATED_OBJECT_PREFIX + moduleSuffix.replaceFirstChar { it.uppercase() }

        // Sort by the fully-qualified reference for deterministic output: KSP discovery order is
        // not stable across builds.
        val sortedReferences = references.sortedBy { it.toString() }
        val setExpression = CodeBlock.builder().add("setOf(\n")
        sortedReferences.forEach { setExpression.add("    %L,\n", it) }
        setExpression.add(")")

        val sensorsProperty = PropertySpec.builder("sensors", SET.parameterizedBy(BASIC_SENSOR))
            .initializer(setExpression.build())
            .build()

        val generatedObject = TypeSpec.objectBuilder(objectName)
            .addModifiers(KModifier.INTERNAL)
            .addProperty(sensorsProperty)
            .build()

        FileSpec.builder(GENERATED_PACKAGE, objectName)
            .addType(generatedObject)
            .build()
            .writeTo(codeGenerator, Dependencies(aggregating = true, *originatingFiles))
    }
}
