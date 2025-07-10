package io.homeassistant.lint.room

import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test

class CoroutineDaoFunctionsIssueTest {
    private val roomAnnotation = kotlin(
        """
        package androidx.room

        @Target(AnnotationTarget.CLASS) @Retention(AnnotationRetention.BINARY) annotation class Dao

        """,
    ).indented()

    private val flow = kotlin(
        """
        package kotlinx.coroutines.flow
        
        interface Flow<T>
        """.trimIndent(),
    )

    @Test
    fun `Given a DAO when fun function is not suspendable and does not return a Flow then CoroutineThreadDaoFunction is raised`() {
        lint().issues(CoroutineDaoFunctionsIssue.ISSUE)
            .allowMissingSdk()
            .files(
                roomAnnotation,
                kotlin(
                    """
              package io.homeassistan.companion.android
                
              import androidx.room.Dao
               
              @Dao
              interface TestDao {
                  fun test()
              }
                    """.trimIndent(),
                ),
            )
            .run()
            .expect(
                """src/io/homeassistan/companion/android/TestDao.kt:7: Error: DAO functions should be suspend or return a Flow. [CoroutineDaoFunction]
    fun test()
    ~~~~~~~~~~
1 error""",
            )
    }

    @Test
    fun `Given a DAO when fun function is suspendable and does not return a Flow then no issues`() {
        lint().issues(CoroutineDaoFunctionsIssue.ISSUE)
            .allowMissingSdk()
            .files(
                roomAnnotation,
                kotlin(
                    """
              package io.homeassistan.companion.android
                
              import androidx.room.Dao
               
              @Dao
              interface TestDao {
                  suspend fun test()
              }
                    """.trimIndent(),
                ),
            )
            .run()
            .expectClean()
    }

    @Test
    fun `Given a DAO when fun function is not suspendable and does return a Flow then no issues`() {
        lint().issues(CoroutineDaoFunctionsIssue.ISSUE)
            .allowMissingSdk()
            .files(
                roomAnnotation,
                flow,
                kotlin(
                    """
              package io.homeassistan.companion.android
                
              import androidx.room.Dao
              import kotlinx.coroutines.flow.Flow
               
              @Dao
              interface TestDao {
                  fun test(): Flow<Int>
              }
                    """.trimIndent(),
                ),
            )
            .run()
            .expectClean()
    }
}
