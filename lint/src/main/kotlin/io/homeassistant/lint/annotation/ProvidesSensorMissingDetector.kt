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
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UField

object ProvidesSensorMissingDetector {

    private const val BASIC_SENSOR_FQN =
        "io.homeassistant.companion.android.common.sensors.SensorManager.BasicSensor"
    private const val PROVIDES_SENSOR_FQN =
        "io.homeassistant.companion.android.common.sensors.ProvidesSensor"

    @JvmField
    val ISSUE = Issue.create(
        id = "ProvidesSensorMissing",
        briefDescription = "BasicSensor not annotated with @ProvidesSensor",
        explanation = """
            Every `SensorManager.BasicSensor` must be annotated `@ProvidesSensor` so the KSP
            processor adds it to the generated `Set<BasicSensor>`. An un-annotated sensor
            silently disappears from the app (e.g. the sensors settings list). To intentionally
            exclude a `BasicSensor`, add `@Suppress("ProvidesSensorMissing")`.
        """.trimIndent(),
        category = Category.CORRECTNESS,
        priority = 10,
        severity = Severity.ERROR,
        implementation = Implementation(IssueDetector::class.java, Scope.JAVA_FILE_SCOPE),
    )

    class IssueDetector :
        Detector(),
        SourceCodeScanner {
        override fun getApplicableUastTypes() = listOf(UField::class.java)

        override fun createUastHandler(context: JavaContext): UElementHandler {
            return object : UElementHandler() {
                override fun visitField(node: UField) {
                    if (node.type.canonicalText != BASIC_SENSOR_FQN) return
                    val annotations = context.evaluator.getAllAnnotations(node as UAnnotated, false)
                    val annotated = annotations.any { it.qualifiedName == PROVIDES_SENSOR_FQN }
                    if (!annotated) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "BasicSensor '${node.name}' must be annotated @ProvidesSensor " +
                                "(or @Suppress(\"ProvidesSensorMissing\") to exclude it)",
                        )
                    }
                }
            }
        }
    }
}
