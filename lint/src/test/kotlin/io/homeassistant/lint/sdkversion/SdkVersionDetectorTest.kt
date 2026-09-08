package io.homeassistant.lint.sdkversion

import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.jupiter.api.Test

class SdkVersionDetectorTest {

    private val buildVersionStub = kotlin(
        """
        package android.os

        class Build {
            class VERSION {
                companion object {
                    @JvmField val SDK_INT: Int = 0
                }
            }
        }
        """,
    ).indented()

    @Test
    fun `Given Build_VERSION_SDK_INT read without suppression then error is reported`() {
        lint().issues(SdkVersionDetector.ISSUE)
            .allowMissingSdk()
            .files(
                buildVersionStub,
                kotlin(
                    """
                    package com.example

                    import android.os.Build

                    fun checkSdk(): Boolean = Build.VERSION.SDK_INT >= 30
                    """,
                ).indented(),
            )
            .run()
            .expect(
                """
                src/com/example/test.kt:5: Error: Read of Build.VERSION.SDK_INT is forbidden. Use SdkVersion.isAtLeast(version) for version gates, or SdkVersion.toString() / "... ${"$"}SdkVersion ..." when you need the raw value as a string. Suppress with @SuppressLint("SdkVersionAccess") only if this site is one of the few legitimate raw accesses. [SdkVersionAccess]
                fun checkSdk(): Boolean = Build.VERSION.SDK_INT >= 30
                                                        ~~~~~~~
                1 error
                """.trimIndent(),
            )
    }

    @Test
    fun `Given SDK_INT read suppressed on enclosing function then no error`() {
        lint().issues(SdkVersionDetector.ISSUE)
            .allowMissingSdk()
            .files(
                buildVersionStub,
                kotlin(
                    """
                    package com.example

                    import android.annotation.SuppressLint
                    import android.os.Build

                    @SuppressLint("SdkVersionAccess")
                    fun checkSdk(): Boolean = Build.VERSION.SDK_INT >= 30
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }

    @Test
    fun `Given SDK_INT read suppressed on enclosing class then no error`() {
        lint().issues(SdkVersionDetector.ISSUE)
            .allowMissingSdk()
            .files(
                buildVersionStub,
                kotlin(
                    """
                    package com.example

                    import android.annotation.SuppressLint
                    import android.os.Build

                    @SuppressLint("SdkVersionAccess")
                    class Helper {
                        fun checkSdk(): Boolean = Build.VERSION.SDK_INT >= 30
                    }
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }

    @Test
    fun `Given an unrelated SDK_INT constant on a different class then no error`() {
        lint().issues(SdkVersionDetector.ISSUE)
            .allowMissingSdk()
            .files(
                kotlin(
                    """
                    package com.example

                    object MyConstants {
                        const val SDK_INT: Int = 42
                    }

                    fun useUnrelatedConstant(): Int = MyConstants.SDK_INT
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }
}
