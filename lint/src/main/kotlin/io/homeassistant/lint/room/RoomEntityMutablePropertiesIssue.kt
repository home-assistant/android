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
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UField
import org.jetbrains.uast.kotlin.getKotlinMemberOrigin

private val ROOM_ENTITY_ANNOTATIONS = listOf(
    "androidx.room.Entity",
    "androidx.room.DatabaseView",
    "androidx.room3.Entity",
    "androidx.room3.DatabaseView",
)

object RoomEntityMutablePropertiesIssue {

    @JvmField
    val ISSUE = Issue.create(
        id = "RoomEntityMutableProperty",
        briefDescription = "Room entity/view properties should be immutable",
        explanation = """
            Room entity and view classes should use immutable properties to avoid mutating persisted state
            in place. Use `val` properties and create updated copies instead.
        """.trimIndent(),
        category = Category.CORRECTNESS,
        severity = Severity.ERROR,
        priority = 10,
        implementation = Implementation(
            IssueDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        ),
    )

    class IssueDetector :
        Detector(),
        SourceCodeScanner {
        override fun getApplicableUastTypes() = listOf(UClass::class.java)

        override fun createUastHandler(context: JavaContext): UElementHandler {
            return object : UElementHandler() {
                override fun visitClass(node: UClass) {
                    if (node.isEntity()) {
                        node.fields
                            .filter { it.isMutableKotlinProperty() }
                            .forEach { field ->
                                context.report(
                                    ISSUE,
                                    field,
                                    context.getNameLocation(field),
                                    "Room entity/view properties should be immutable. Use `val` instead of `var`.",
                                )
                            }
                    }
                }
            }
        }
    }
}

private fun UClass.isEntity(): Boolean {
    return ROOM_ENTITY_ANNOTATIONS.any(::hasAnnotation)
}

private fun UField.isMutableKotlinProperty(): Boolean {
    return when (val origin = javaPsi?.originalElement?.let(::getKotlinMemberOrigin)) {
        is KtProperty -> origin.isVar
        is KtParameter -> origin.isMutable
        else -> false
    }
}
