package io.homeassistant.lint.annotation

import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.lint.checks.infrastructure.TestLintTask
import org.junit.Test

class CatalogSensorMissingDetectorTest {

    private val stubs = TestFiles.kotlin(
        """
        package io.homeassistant.companion.android.common.sensors
        annotation class CatalogSensor
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
        ).issues(CatalogSensorMissingDetector.ISSUE).run().expectErrorCount(1)
    }

    @Test
    fun `Given annotated BasicSensor val then clean`() {
        TestLintTask.lint().files(
            stubs,
            TestFiles.kotlin(
                """
                package io.homeassistant.companion.android.sensors
                import io.homeassistant.companion.android.common.sensors.CatalogSensor
                import io.homeassistant.companion.android.common.sensors.SensorManager
                class FooManager {
                    companion object {
                        @CatalogSensor val alpha = SensorManager.BasicSensor("alpha")
                    }
                }
                """,
            ).indented(),
        ).issues(CatalogSensorMissingDetector.ISSUE).run().expectClean()
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
                        @Suppress("CatalogSensorMissing")
                        val alpha = SensorManager.BasicSensor("alpha")
                    }
                }
                """,
            ).indented(),
        ).issues(CatalogSensorMissingDetector.ISSUE).run().expectClean()
    }
}
