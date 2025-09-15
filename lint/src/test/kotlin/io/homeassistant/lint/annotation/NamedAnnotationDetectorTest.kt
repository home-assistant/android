package io.homeassistant.lint.annotation

import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test

class NamedAnnotationDetectorTest {

    private val namedAnnotationStub = kotlin(
        """
        package javax.inject

        @Retention(AnnotationRetention.RUNTIME)
        annotation class Named(val value: String = "")
        """,
    ).indented()

    private val qualifierAnnotationStub = kotlin(
        """
    package javax.inject

    @Target(AnnotationTarget.ANNOTATION_CLASS)
    @Retention(AnnotationRetention.RUNTIME)
    annotation class Qualifier
    """,
    ).indented()

    private val customQualifierStub = kotlin(
        """
        package io.homeassistant.companion.android.di.qualifiers

        import javax.inject.Qualifier

        @Qualifier
        @Retention(AnnotationRetention.BINARY)
        annotation class MyCustomQualifier
        """,
    ).indented()

    private val injectAnnotationStub = kotlin(
        """
    package javax.inject

    @Target(
        AnnotationTarget.CONSTRUCTOR,
        AnnotationTarget.FIELD,
        AnnotationTarget.FUNCTION
    )
    @Retention(AnnotationRetention.RUNTIME)
    annotation class Inject
    """,
    ).indented()

    @Test
    fun `Given a class using @Named annotation then NoNamedAnnotation issue is raised`() {
        lint().issues(NamedAnnotationDetector.ISSUE)
            .allowMissingSdk()
            .files(
                qualifierAnnotationStub,
                injectAnnotationStub,
                namedAnnotationStub,
                kotlin(
                    """
                    package io.homeassistant.companion.android.data

                    import javax.inject.Inject
                    import javax.inject.Named

                    class MyRepository @Inject constructor(
                        @Named("server_url") private val serverUrl: String
                    )
                    """,
                ).indented(),
            )
            .run()
            .expect(
                """
                src/io/homeassistant/companion/android/data/MyRepository.kt:7: Error: Usage of @Named is forbidden. Use a custom qualifier annotation instead. [NoNamedAnnotation]
                    @Named("server_url") private val serverUrl: String
                    ~~~~~~~~~~~~~~~~~~~~
                1 error
                """.trimIndent(),
            )
    }

    @Test
    fun `Given a class using a custom qualifier annotation then no issues`() {
        lint().issues(NamedAnnotationDetector.ISSUE)
            .allowMissingSdk()
            .files(
                qualifierAnnotationStub,
                injectAnnotationStub,
                customQualifierStub,
                kotlin(
                    """
                    package io.homeassistant.companion.android.data

                    import javax.inject.Inject
                    import io.homeassistant.companion.android.di.qualifiers.MyCustomQualifier

                    class MyRepository @Inject constructor(
                        @MyCustomQualifier private val serverUrl: String
                    )
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }
}
