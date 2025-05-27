package io.homeassistant.lint.serialization

import com.android.tools.lint.checks.infrastructure.LintDetectorTest.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test

class MissingSerializableAnnotationIssueTest {
    private val issuesToUse = arrayOf(
        MissingSerializableAnnotationIssue.ISSUE,
        MissingSerializableAnnotationIssue.RECOMMENDATION,
    )

    private val serializableStub = kotlin(
        """
        package kotlinx.serialization

        @Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
        @Retention(AnnotationRetention.RUNTIME)
        annotation class Serializable
        """,
    ).indented()

    private val jsonStub = kotlin(
        """
        package kotlinx.serialization.json
        
        class SerializationStrategy<T>
        
        object Json {
            fun <T> encodeToString(serializer: SerializationStrategy<T> = SerializationStrategy(), value: T): String {
                return "hello" 
            }
        }

        fun Json(builder : ()-> Unit): Json {
            return Json
        }
        """,
    ).indented()

    private val kotlinJsonMapperStub = kotlin(
        """
        package io.homeassistant.companion.android.common.util
        import kotlinx.serialization.json.Json

        val kotlinJsonMapper = Json {}

        object MapAnySerializer
        object AnySerializer
        """,
    ).indented()

    @Test
    fun `Given a class with Serializable annotation when invoking Json encodeToString then no issue`() {
        lint().issues(*issuesToUse)
            .allowMissingSdk()
            .files(
                serializableStub,
                jsonStub,
                kotlin(
                    """
                package io.homeassistant
                import kotlinx.serialization.json.Json
                import kotlinx.serialization.Serializable
                
                @Serializable
                class Home

                fun main() {
                    val value = Json.encodeToString(Home())
                    println(value)
                }
                """,
                ).indented(),
            )
            .run()
            .expectClean()
    }

    @Test
    fun `Given a class with Serializable annotation when invoking kotlinJsonMapper encodeToString then no issue`() {
        lint().issues(*issuesToUse)
            .allowMissingSdk()
            .files(
                serializableStub,
                kotlinJsonMapperStub,
                jsonStub,
                kotlin(
                    """
                package io.homeassistant
                import io.homeassistant.companion.android.common.util.kotlinJsonMapper
                import kotlinx.serialization.Serializable

                @Serializable
                class Home

                fun main() {
                    val value = kotlinJsonMapper.encodeToString(Home())
                    println(value)
                }
                """,
                ).indented(),
            )
            .run()
            .expectClean()
    }

    @Test
    fun `Given a class without Serializable annotation when invoking Json encodeToString then MissingSerializableAnnotationIssue is raised`() {
        lint().issues(*issuesToUse)
            .allowMissingSdk()
            .files(
                serializableStub,
                jsonStub,
                kotlin(
                    """
                package io.homeassistant
                import kotlinx.serialization.json.Json
                
                class Home
                
                fun main() {
                    val value = Json.encodeToString(Home())
                    println(value)
                }
                """,
                ).indented(),
            )
            .run()
            .expect(
                """src/io/homeassistant/Home.kt:7: Error: The class io.homeassistant.Home is missing the @Serializable annotation. [MissingSerializableAnnotation]
    val value = Json.encodeToString(Home())
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~
1 errors, 0 warnings""",
            )
    }

    @Test
    fun `Given a class without Serializable annotation when invoking kotlinJsonMapper encodeToString then MissingSerializableAnnotationIssue is raised`() {
        lint().issues(*issuesToUse)
            .allowMissingSdk()
            .files(
                serializableStub,
                kotlinJsonMapperStub,
                jsonStub,
                kotlin(
                    """
                package io.homeassistant
                import io.homeassistant.companion.android.common.util.kotlinJsonMapper                

                class Home
                fun main() {
                    val value = kotlinJsonMapper.encodeToString(Home())
                    println(value)
                }
                """,
                ).indented(),
            )
            .run()
            .expect(
                """src/io/homeassistant/Home.kt:6: Error: The class io.homeassistant.Home is missing the @Serializable annotation. [MissingSerializableAnnotation]
    val value = kotlinJsonMapper.encodeToString(Home())
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
1 errors, 0 warnings""",
            )
    }

    @Test
    fun `Given a sub class without Serializable annotation when invoking kotlinJsonMapper encodeToString then MissingSerializableAnnotationIssue is raised`() {
        lint().issues(*issuesToUse)
            .allowMissingSdk()
            .files(
                serializableStub,
                kotlinJsonMapperStub,
                jsonStub,
                kotlin(
                    """
                package io.homeassistant
                import io.homeassistant.companion.android.common.util.kotlinJsonMapper                
                import kotlinx.serialization.Serializable

                @Serializable
                class Home

                
                class ConnectedHome: Home
                fun main() {
                    val value = kotlinJsonMapper.encodeToString<Home>(ConnectedHome())
                    println(value)
                }
                """,
                ).indented(),
            )
            .run()
            .expect(
                """src/io/homeassistant/Home.kt:11: Error: The class io.homeassistant.ConnectedHome is missing the @Serializable annotation. [MissingSerializableAnnotation]
    val value = kotlinJsonMapper.encodeToString<Home>(ConnectedHome())
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
1 errors, 0 warnings""",
            )
    }

    @Test
    fun `Given a class without Serializable annotation when invoking kotlinJsonMapper encodeToString with custom serializer then MissingSerializableAnnotationIssue is raised`() {
        lint().issues(*issuesToUse)
            .allowMissingSdk()
            .files(
                serializableStub,
                kotlinJsonMapperStub,
                jsonStub,
                kotlin(
                    """
                package io.homeassistant
                import io.homeassistant.companion.android.common.util.kotlinJsonMapper
                import kotlinx.serialization.json.SerializationStrategy
                
                class Home
                fun main() {
                    val value = kotlinJsonMapper.encodeToString(value = Home(), SerializationStrategy<Home>())
                    println(value)
                }
                """,
                ).indented(),
            )
            .run()
            .expect(
                """src/io/homeassistant/Home.kt:7: Error: The class io.homeassistant.Home is missing the @Serializable annotation. [MissingSerializableAnnotation]
    val value = kotlinJsonMapper.encodeToString(value = Home(), SerializationStrategy<Home>())
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
1 errors, 0 warnings""",
            )
    }

    @Test
    fun `Given a class without Serializable annotation within a Map when invoking kotlinJsonMapper encodeToString then MissingSerializableAnnotationIssue is raised`() {
        lint().issues(*issuesToUse)
            .allowMissingSdk()
            .files(
                serializableStub,
                kotlinJsonMapperStub,
                jsonStub,
                kotlin(
                    """
                package io.homeassistant
                import io.homeassistant.companion.android.common.util.kotlinJsonMapper
                import kotlinx.serialization.json.SerializationStrategy
                
                class Home
                fun main() {
                    val value = kotlinJsonMapper.encodeToString(mapOf<String, Home>( "hello" to Home()))
                    println(value)
                }
                """,
                ).indented(),
            )
            .run()
            .expect(
                """src/io/homeassistant/Home.kt:7: Error: The class io.homeassistant.Home is missing the @Serializable annotation. [MissingSerializableAnnotation]
    val value = kotlinJsonMapper.encodeToString(mapOf<String, Home>( "hello" to Home()))
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
1 errors, 0 warnings""",
            )
    }

    @Test
    fun `Given a list of a class without Serializable annotation within a Map when invoking kotlinJsonMapper encodeToString then MissingSerializableAnnotationIssue is raised`() {
        lint().issues(*issuesToUse)
            .allowMissingSdk()
            .files(
                serializableStub,
                kotlinJsonMapperStub,
                jsonStub,
                kotlin(
                    """
                package io.homeassistant
                import io.homeassistant.companion.android.common.util.kotlinJsonMapper
                import kotlinx.serialization.json.SerializationStrategy
                import java.util.List
                
                class Home
                fun main() {
                    val value = kotlinJsonMapper.encodeToString(mapOf<String, List<Home>>( "hello" to Home()))
                    println(value)
                }
                """,
                ).indented(),
            )
            .run()
            .expect(
                """src/io/homeassistant/Home.kt:8: Error: The class io.homeassistant.Home is missing the @Serializable annotation. [MissingSerializableAnnotation]
    val value = kotlinJsonMapper.encodeToString(mapOf<String, List<Home>>( "hello" to Home()))
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
1 errors, 0 warnings""",
            )
    }

    @Test
    fun `Given the usage of any when invoking kotlinJsonMapper encoreToString with a MapAnySerializer then hint to avoid AnySerializer is logged`() {
        lint().issues(*issuesToUse)
            .allowMissingSdk()
            .files(
                serializableStub,
                kotlinJsonMapperStub,
                jsonStub,
                kotlin(
                    """
                package io.homeassistant
                import io.homeassistant.companion.android.common.util.kotlinJsonMapper
                import io.homeassistant.companion.android.common.util.MapAnySerializer
                import kotlinx.serialization.json.SerializationStrategy
                import java.util.List
                
                class Home
                fun main() {
                    val value = kotlinJsonMapper.encodeToString(MapAnySerializer, value = mapOf<String, List<Home>>( "hello" to Home()))
                    println(value)
                }
                """,
                ).indented(),
            )
            .run()
            .expect(
                """src/io/homeassistant/Home.kt:9: Hint: Prefer polymorphic serializer over AnySerializer. [AvoidAnySerializer]
    val value = kotlinJsonMapper.encodeToString(MapAnySerializer, value = mapOf<String, List<Home>>( "hello" to Home()))
                                                ~~~~~~~~~~~~~~~~
0 errors, 0 warnings, 1 hint""",
            )
    }
}
