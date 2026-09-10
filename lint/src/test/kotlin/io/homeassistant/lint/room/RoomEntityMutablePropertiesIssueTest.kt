package io.homeassistant.lint.room

import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Runs a parameterized test once for the `androidx.room` package and once for the `androidx.room3`
 * package, injecting the package name as the test argument.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ParameterizedTest
@ValueSource(strings = ["androidx.room", "androidx.room3"])
private annotation class RoomPackageTest

class RoomEntityMutablePropertiesIssueTest {
    private fun roomAnnotations(packageName: String) = kotlin(
        """
        package $packageName

        @Target(AnnotationTarget.CLASS)
        @Retention(AnnotationRetention.BINARY)
        annotation class Entity(val tableName: String = "")

        @Target(AnnotationTarget.CLASS)
        @Retention(AnnotationRetention.BINARY)
        annotation class DatabaseView(val value: String = "", val viewName: String = "")

        """,
    ).indented()

    @RoomPackageTest
    fun `Given a Room entity when constructor property is var then RoomEntityMutableProperty issue is raised`(roomPackage: String) {
        lint().issues(RoomEntityMutablePropertiesIssue.ISSUE)
            .allowMissingSdk()
            .files(
                roomAnnotations(roomPackage),
                kotlin(
                    """
                    package io.homeassistant

                    import $roomPackage.Entity

                    @Entity(tableName = "homes")
                    data class Home(
                        val id: Int,
                        var name: String,
                    )
                    """,
                ).indented(),
            )
            .run()
            .expect(
                """src/io/homeassistant/Home.kt:8: Error: Room entity/view properties should be immutable. Use val instead of var. [RoomEntityMutableProperty]
    var name: String,
        ~~~~
1 error""",
            )
    }

    @RoomPackageTest
    fun `Given a Room database view when constructor property is var then RoomEntityMutableProperty issue is raised`(roomPackage: String) {
        lint().issues(RoomEntityMutablePropertiesIssue.ISSUE)
            .allowMissingSdk()
            .files(
                roomAnnotations(roomPackage),
                kotlin(
                    """
                    package io.homeassistant

                    import $roomPackage.DatabaseView

                    @DatabaseView("SELECT id, name FROM homes")
                    data class HomeView(
                        val id: Int,
                        var name: String,
                    )
                    """,
                ).indented(),
            )
            .run()
            .expect(
                """src/io/homeassistant/HomeView.kt:8: Error: Room entity/view properties should be immutable. Use val instead of var. [RoomEntityMutableProperty]
    var name: String,
        ~~~~
1 error""",
            )
    }

    @RoomPackageTest
    fun `Given a Room entity when body property is var then RoomEntityMutableProperty issue is raised`(roomPackage: String) {
        lint().issues(RoomEntityMutablePropertiesIssue.ISSUE)
            .allowMissingSdk()
            .files(
                roomAnnotations(roomPackage),
                kotlin(
                    """
                    package io.homeassistant

                    import $roomPackage.Entity

                    @Entity
                    class Home {
                        var name: String = ""
                    }
                    """,
                ).indented(),
            )
            .run()
            .expect(
                """src/io/homeassistant/Home.kt:7: Error: Room entity/view properties should be immutable. Use val instead of var. [RoomEntityMutableProperty]
    var name: String = ""
        ~~~~
1 error""",
            )
    }

    @RoomPackageTest
    fun `Given a Room entity when properties are val then no issues`(roomPackage: String) {
        lint().issues(RoomEntityMutablePropertiesIssue.ISSUE)
            .allowMissingSdk()
            .files(
                roomAnnotations(roomPackage),
                kotlin(
                    """
                    package io.homeassistant

                    import $roomPackage.Entity

                    @Entity
                    data class Home(
                        val id: Int,
                        val name: String,
                    )
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }

    @RoomPackageTest
    fun `Given a non Room entity class when constructor property is var then no issues`(roomPackage: String) {
        lint().issues(RoomEntityMutablePropertiesIssue.ISSUE)
            .allowMissingSdk()
            .files(
                roomAnnotations(roomPackage),
                kotlin(
                    """
                    package io.homeassistant

                    data class Home(
                        val id: Int,
                        var name: String,
                    )
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }
}
