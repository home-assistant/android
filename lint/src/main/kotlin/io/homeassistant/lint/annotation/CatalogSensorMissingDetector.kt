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

object CatalogSensorMissingDetector {

    private const val BASIC_SENSOR_FQN =
        "io.homeassistant.companion.android.common.sensors.SensorManager.BasicSensor"
    private const val CATALOG_SENSOR_FQN =
        "io.homeassistant.companion.android.common.sensors.CatalogSensor"

    @JvmField
    val ISSUE = Issue.create(
        id = "CatalogSensorMissing",
        briefDescription = "BasicSensor not registered in the sensor catalog",
        explanation = """
            Every `SensorManager.BasicSensor` must be annotated `@CatalogSensor` so the KSP
            processor adds it to the generated `Set<BasicSensor>` catalog. An un-annotated sensor
            silently disappears from the app (e.g. the sensors settings list). To intentionally
            exclude a `BasicSensor`, add `@Suppress("CatalogSensorMissing")`.
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
                    val annotated = annotations.any { it.qualifiedName == CATALOG_SENSOR_FQN }
                    if (!annotated) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "BasicSensor '${node.name}' must be annotated @CatalogSensor " +
                                "(or @Suppress(\"CatalogSensorMissing\") to exclude it)",
                        )
                    }
                }
            }
        }
    }
}
