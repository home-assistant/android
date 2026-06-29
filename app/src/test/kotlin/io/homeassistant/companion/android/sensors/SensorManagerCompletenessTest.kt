package io.homeassistant.companion.android.sensors

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.github.classgraph.ClassGraph
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.testing.unit.seedFakeAndroidId
import javax.inject.Inject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards against forgetting a `@Binds @IntoSet` for a new [SensorManager]: every concrete
 * implementation on the variant's classpath must be present in the Hilt-injected
 * `Set<SensorManager>`. Runs for each `:app` variant, so `full` and `minimal` each verify their own
 * complete set. ClassGraph is used because neither Hilt nor reflection can enumerate an interface's
 * implementations — only a classpath scan can.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class SensorManagerCompletenessTest {
    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject
    lateinit var managers: Set<@JvmSuppressWildcards SensorManager>

    @Before
    fun setup() {
        ApplicationProvider.getApplicationContext<Context>().seedFakeAndroidId()
    }

    @Test
    fun `Given every SensorManager on the classpath then each is bound into the injected set`() {
        hilt.inject()

        val bound = managers.map { it::class.java.name }.toSet()
        val expected = ClassGraph()
            .enableClassInfo()
            .acceptPackages("io.homeassistant.companion.android")
            .scan()
            .use { result ->
                result.getClassesImplementing(SensorManager::class.java)
                    .standardClasses
                    .filter { !it.isAbstract }
                    .map { it.name }
                    .toSet()
            }

        assertTrue("ClassGraph found no SensorManager implementations - scan misconfigured?", expected.isNotEmpty())
        assertEquals(
            "SensorManager(s) missing a @Binds @IntoSet binding: ${expected - bound}",
            emptySet<String>(),
            expected - bound,
        )
        // No duplicate manager bindings.
        assertEquals(bound.size, managers.size)
    }
}
