package io.homeassistant.lint.annotation

import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.lint.checks.infrastructure.TestLintTask
import org.junit.jupiter.api.Test

class ProvidesSensorMissingDetectorTest {

    private val stubs = TestFiles.kotlin(
        """
        package io.homeassistant.companion.android.common.sensors
        annotation class ProvidesSensor
        class SensorManager { class BasicSensor(val id: String) }
        """,
    ).indented()

    @Test
    fun `Given un-annotated BasicSensor val then reports error`() {
        TestLintTask.lint().files(
            stubs,
            TestFiles.kotlin(
                """
                package io.homeassistant.companion.android.sensors
                import io.homeassistant.companion.android.common.sensors.SensorManager
                class FooManager {
                    companion object {
                        val alpha = SensorManager.BasicSensor("alpha")
                    }
                }
                """,
            ).indented(),
        ).issues(ProvidesSensorMissingDetector.ISSUE).run().expectErrorCount(1)
    }

    @Test
    fun `Given annotated BasicSensor val then clean`() {
        TestLintTask.lint().files(
            stubs,
            TestFiles.kotlin(
                """
                package io.homeassistant.companion.android.sensors
                import io.homeassistant.companion.android.common.sensors.ProvidesSensor
                import io.homeassistant.companion.android.common.sensors.SensorManager
                class FooManager {
                    companion object {
                        @ProvidesSensor val alpha = SensorManager.BasicSensor("alpha")
                    }
                }
                """,
            ).indented(),
        ).issues(ProvidesSensorMissingDetector.ISSUE).run().expectClean()
    }

    @Test
    fun `Given BasicSensor field without initializer then clean`() {
        TestLintTask.lint().files(
            stubs,
            TestFiles.kotlin(
                """
                package io.homeassistant.companion.android.sensors
                import io.homeassistant.companion.android.common.sensors.SensorManager
                data class SensorWrapper(val sensor: SensorManager.BasicSensor)
                """,
            ).indented(),
        ).issues(ProvidesSensorMissingDetector.ISSUE).run().expectClean()
    }

    @Test
    fun `Given lateinit BasicSensor field then clean`() {
        TestLintTask.lint().files(
            stubs,
            TestFiles.kotlin(
                """
                package io.homeassistant.companion.android.sensors
                import io.homeassistant.companion.android.common.sensors.SensorManager
                class FooManager {
                    lateinit var held: SensorManager.BasicSensor
                }
                """,
            ).indented(),
        ).issues(ProvidesSensorMissingDetector.ISSUE).run().expectClean()
    }

    @Test
    fun `Given BasicSensor field built by a factory call then reports error`() {
        TestLintTask.lint().files(
            stubs,
            TestFiles.kotlin(
                """
                package io.homeassistant.companion.android.sensors
                import io.homeassistant.companion.android.common.sensors.SensorManager
                class FooManager {
                    companion object {
                        fun factory(): SensorManager.BasicSensor = SensorManager.BasicSensor("a")
                        val alpha = factory()
                    }
                }
                """,
            ).indented(),
        ).issues(ProvidesSensorMissingDetector.ISSUE).run().expectErrorCount(1)
    }

    @Test
    fun `Given un-annotated val when suppressed then clean`() {
        TestLintTask.lint().files(
            stubs,
            TestFiles.kotlin(
                """
                package io.homeassistant.companion.android.sensors
                import io.homeassistant.companion.android.common.sensors.SensorManager
                class FooManager {
                    companion object {
                        @Suppress("ProvidesSensorMissing")
                        val alpha = SensorManager.BasicSensor("alpha")
                    }
                }
                """,
            ).indented(),
        ).issues(ProvidesSensorMissingDetector.ISSUE).run().expectClean()
    }
}
