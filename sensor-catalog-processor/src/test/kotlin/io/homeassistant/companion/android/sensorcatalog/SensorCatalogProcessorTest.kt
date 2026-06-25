package io.homeassistant.companion.android.sensorcatalog

import com.google.devtools.ksp.impl.KotlinSymbolProcessing
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPJvmConfig
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSNode
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

private const val SENSORS_PACKAGE = "io.homeassistant.companion.android.sensors"

/**
 * Header shared by every test source: the sensors package plus the imports that the annotated
 * declarations rely on. Test bodies only need to declare their sensors. See [writeSensorSource].
 */
private val SENSOR_SOURCE_HEADER = listOf(
    "package $SENSORS_PACKAGE",
    "import io.homeassistant.companion.android.common.sensors.CatalogSensor",
    "import io.homeassistant.companion.android.common.sensors.SensorManager",
).joinToString(separator = "\n", postfix = "\n")

/**
 * Drives the [SensorCatalogProcessor] end-to-end through the KSP2 [KotlinSymbolProcessing] entry
 * point. We avoid a third-party compile-testing harness and instead reuse the KSP version pinned by
 * the project, feeding the processor real Kotlin sources and inspecting the generated output.
 */
class SensorCatalogProcessorTest {

    @Test
    fun `Given annotated companion vals when processed then generated catalog references them`(
        @TempDir workingDir: File,
    ) {
        workingDir.writeStub()
        workingDir.writeSensorSource(
            "FooManager.kt",
            """
            class FooManager {
                companion object {
                    @CatalogSensor val alpha = SensorManager.BasicSensor("alpha")
                    @CatalogSensor val beta = SensorManager.BasicSensor("beta")
                }
            }
            """,
        )

        val result = workingDir.runProcessor()

        assertEquals(KotlinSymbolProcessing.ExitCode.OK, result.exitCode, result.messages.joinToString("\n"))
        val generated = workingDir.generatedFile("GeneratedSensorCatalogTest.kt").readText()
        assertTrue(generated.contains("internal object GeneratedSensorCatalogTest"), generated)
        assertTrue(generated.contains("val sensors"), generated)
        assertTrue(generated.contains("setOf("), generated)
        assertTrue(generated.contains("FooManager.Companion.alpha"), generated)
        assertTrue(generated.contains("FooManager.Companion.beta"), generated)
    }

    @Test
    fun `Given annotated sensors in all referenceable forms when processed then catalog references them sorted`(
        @TempDir workingDir: File,
    ) {
        workingDir.writeStub()
        // Covers all three referenceable forms: object val, companion-object val, and top-level val.
        workingDir.writeSensorSource(
            "FooManager.kt",
            """
            object FooManager {
                @CatalogSensor val alpha = SensorManager.BasicSensor("alpha")
            }

            class BarManager {
                companion object {
                    @CatalogSensor val beta = SensorManager.BasicSensor("beta")
                }
            }
            @CatalogSensor val gamma = SensorManager.BasicSensor("gamma")
            """,
        )

        val result = workingDir.runProcessor()

        assertEquals(KotlinSymbolProcessing.ExitCode.OK, result.exitCode, result.messages.joinToString("\n"))
        val generated = workingDir.generatedFile("GeneratedSensorCatalogTest.kt").readText()
        // Golden source documenting the generated module. Object and companion-object vals are
        // referenced qualified by their declaring type (a member import of a companion-object member
        // does not compile), so a companion resolves to `BarManager.Companion.beta`; only the
        // top-level gamma is imported directly.
        val expected = """
            package io.homeassistant.`companion`.android.sensorcatalog.generated

            import io.homeassistant.`companion`.android.common.sensors.SensorManager
            import io.homeassistant.`companion`.android.sensors.BarManager
            import io.homeassistant.`companion`.android.sensors.FooManager
            import io.homeassistant.`companion`.android.sensors.gamma
            import kotlin.collections.Set

            internal object GeneratedSensorCatalogTest {
              public val sensors: Set<SensorManager.BasicSensor> = setOf(
                      BarManager.Companion.beta,
                      FooManager.alpha,
                      gamma,
                  )
            }
        """.trimIndent() + "\n"
        assertEquals(expected, generated)
    }

    @Test
    fun `Given instance property when processed then fails with referenceability error`(
        @TempDir workingDir: File,
    ) {
        workingDir.writeStub()
        workingDir.writeSensorSource(
            "BarManager.kt",
            """
            class BarManager {
                @CatalogSensor val instanceSensor = SensorManager.BasicSensor("x")
            }
            """,
        )

        val result = workingDir.runProcessor()

        assertTrue(
            result.errors.any { it.contains("top-level, object, or companion-object val") },
            result.messages.joinToString("\n"),
        )
    }

    @Test
    fun `Given a private val when processed then fails with referenceability error`(
        @TempDir workingDir: File,
    ) {
        workingDir.writeStub()
        workingDir.writeSensorSource(
            "SecretManager.kt",
            """
            object SecretManager {
                @CatalogSensor private val secret = SensorManager.BasicSensor("secret")
            }
            """,
        )

        val result = workingDir.runProcessor()

        assertTrue(
            result.errors.any { it.contains("so it can be referenced from generated code") },
            result.messages.joinToString("\n"),
        )
    }

    @Test
    fun `Given annotation on a non-BasicSensor property when processed then fails with type error`(
        @TempDir workingDir: File,
    ) {
        workingDir.writeStub()
        workingDir.writeSensorSource(
            "NotASensor.kt",
            """
            @CatalogSensor val notASensor = "I am not a BasicSensor"
            """,
        )

        val result = workingDir.runProcessor()

        assertTrue(
            result.errors.any { it.contains("can only annotate a SensorManager.BasicSensor val") },
            result.messages.joinToString("\n"),
        )
    }

    @Test
    fun `Given a sensor that resolves in a later round when processed then it joins the single module`(
        @TempDir workingDir: File,
    ) {
        workingDir.writeStub()
        // Resolves in round 1.
        workingDir.writeSensorSource(
            "EagerManager.kt",
            """
            object EagerManager {
                @CatalogSensor val eager = SensorManager.BasicSensor("eager")
            }
            """,
        )

        // The companion generator emits a second annotated sensor as a generated source, so the
        // processor only sees it in round 2. Generating per round would write the module twice (a
        // FileAlreadyExistsException) and drop the round-1 sensor.
        val result = workingDir.runProcessor(RoundTwoSensorGeneratorProvider())

        assertEquals(KotlinSymbolProcessing.ExitCode.OK, result.exitCode, result.messages.joinToString("\n"))
        val generated = workingDir.generatedFile("GeneratedSensorCatalogTest.kt").readText()
        assertTrue(generated.contains("EagerManager.eager"), generated)
        assertTrue(generated.contains("LateManager.late"), generated)
    }

    /** Writes the stub `common.sensors` package so the annotated sources have something to resolve against. */
    private fun File.writeStub() = writeSource(
        "Stubs.kt",
        """
        package io.homeassistant.companion.android.common.sensors
        @Target(AnnotationTarget.PROPERTY)
        @Retention(AnnotationRetention.SOURCE)
        annotation class CatalogSensor
        class SensorManager { class BasicSensor(val id: String) }
        """.trimIndent(),
    )

    /** Writes an annotated sensor source under the sensors package, prepending [SENSOR_SOURCE_HEADER]. */
    private fun File.writeSensorSource(fileName: String, body: String) = writeSource(fileName, SENSOR_SOURCE_HEADER + body.trimIndent())

    private fun File.writeSource(name: String, content: String) {
        val sourceDir = resolve("sources").apply { mkdirs() }
        sourceDir.resolve(name).writeText(content)
    }

    private fun File.generatedFile(name: String): File = resolve("kspOutput/kotlin").walkTopDown().first { it.name == name }

    private fun File.runProcessor(vararg extraProviders: SymbolProcessorProvider): ProcessorResult {
        val sourceDir = resolve("sources").apply { mkdirs() }
        val collectingLogger = CollectingLogger()

        val config = KSPJvmConfig.Builder().apply {
            moduleName = "test"
            sourceRoots = listOf(sourceDir)
            projectBaseDir = this@runProcessor
            outputBaseDir = resolve("kspOutput")
            cachesDir = resolve("kspCaches")
            classOutputDir = resolve("kspOutput/classes")
            kotlinOutputDir = resolve("kspOutput/kotlin")
            resourceOutputDir = resolve("kspOutput/resources")
            javaOutputDir = resolve("kspOutput/java")
            // Classpath of the test JVM so the stub sources resolve against kotlin-stdlib etc.
            libraries = System.getProperty("java.class.path").split(File.pathSeparator).map(::File)
            processorOptions = mapOf("catalogModuleSuffix" to "Test")
            jvmTarget = "17"
            languageVersion = "2.1"
            apiVersion = "2.1"
        }.build()

        val exitCode = KotlinSymbolProcessing(
            config,
            listOf(SensorCatalogProcessorProvider(), *extraProviders),
            collectingLogger,
        ).execute()

        return ProcessorResult(exitCode, collectingLogger.messages, collectingLogger.errors)
    }

    private data class ProcessorResult(
        val exitCode: KotlinSymbolProcessing.ExitCode,
        val messages: List<String>,
        val errors: List<String>,
    )

    private class RoundTwoSensorGeneratorProvider : SymbolProcessorProvider {
        override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor = RoundTwoSensorGenerator(environment.codeGenerator)
    }

    /**
     * Emits a single `@CatalogSensor` source on its first round so that [SensorCatalogProcessor]
     * encounters that sensor only in a subsequent round, exercising cross-round accumulation.
     */
    private class RoundTwoSensorGenerator(private val codeGenerator: CodeGenerator) : SymbolProcessor {
        private var generated = false

        override fun process(resolver: Resolver): List<KSAnnotated> {
            if (!generated) {
                generated = true
                codeGenerator.createNewFile(Dependencies(aggregating = false), SENSORS_PACKAGE, "LateManager")
                    .use { output ->
                        output.write(
                            (
                                SENSOR_SOURCE_HEADER +
                                    """
                                    object LateManager {
                                        @CatalogSensor val late = SensorManager.BasicSensor("late")
                                    }
                                    """.trimIndent()
                                ).toByteArray(),
                        )
                    }
            }
            return emptyList()
        }
    }

    private class CollectingLogger : KSPLogger {
        val messages = mutableListOf<String>()
        val errors = mutableListOf<String>()

        override fun logging(message: String, symbol: KSNode?) {
            messages += message
        }

        override fun info(message: String, symbol: KSNode?) {
            messages += message
        }

        override fun warn(message: String, symbol: KSNode?) {
            messages += message
        }

        override fun error(message: String, symbol: KSNode?) {
            messages += message
            errors += message
        }

        override fun exception(e: Throwable) {
            messages += (e.message ?: e.toString())
        }
    }
}
