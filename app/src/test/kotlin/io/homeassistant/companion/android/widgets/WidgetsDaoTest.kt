package io.homeassistant.companion.android.widgets

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.database.widget.WidgetDao
import io.homeassistant.companion.android.testing.unit.ConsoleLogRule
import java.util.jar.JarFile
import javax.inject.Inject
import junit.framework.TestCase.assertEquals
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class WidgetsDaoTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @get:Rule
    var consoleLog = ConsoleLogRule()

    @Inject
    lateinit var widgetDaos: Set<@JvmSuppressWildcards WidgetDao<*>>

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun `Given injected widgetDaos then all WidgetDao interfaces are present`() {
        val allWidgetDaoInterfaces = getAllWidgetDaoInterfaces()

        // Extract the DAO interface from each injected _Impl class
        val injectedDaoInterfaces = widgetDaos.map { dao ->
            dao::class.supertypes
                .mapNotNull { it.classifier as? KClass<*> }
                .first { it.isSubclassOf(WidgetDao::class) && it != WidgetDao::class }
        }.toSet()

        assertEquals(allWidgetDaoInterfaces, injectedDaoInterfaces)
    }

    /**
     * Uses reflection to find all interfaces in the widget package that extend [WidgetDao].
     * This scans the classpath for classes in `io.homeassistant.companion.android`.
     */
    private fun getAllWidgetDaoInterfaces(): Set<KClass<*>> {
        val widgetPackage = "io.homeassistant.companion.android"
        val classLoader = Thread.currentThread().contextClassLoader
            ?: return emptySet()

        return findClassesInPackage(widgetPackage, classLoader)
            .filter { clazz ->
                clazz.isInterface &&
                    clazz.kotlin.isSubclassOf(WidgetDao::class) &&
                    clazz.kotlin != WidgetDao::class
            }
            .map { it.kotlin }
            .toSet()
    }

    private fun findClassesInPackage(packageName: String, classLoader: ClassLoader): List<Class<*>> {
        val path = packageName.replace('.', '/')
        val resources = classLoader.getResources(path)
        val classes = mutableListOf<Class<*>>()

        while (resources.hasMoreElements()) {
            val resource = resources.nextElement()
            if (resource.protocol == "jar") {
                val jarPath = resource.path.substringBefore("!").removePrefix("file:")
                classes.addAll(findClassesInJar(jarPath, packageName, classLoader))
            }
        }
        return classes
    }

    private fun findClassesInJar(
        jarPath: String,
        packageName: String,
        classLoader: ClassLoader,
    ): List<Class<*>> {
        val path = packageName.replace('.', '/')
        return JarFile(jarPath).use { jar ->
            jar.entries().asSequence()
                .filter { it.name.startsWith(path) && it.name.endsWith(".class") }
                .mapNotNull { entry ->
                    val className = entry.name
                        .removeSuffix(".class")
                        .replace('/', '.')
                    runCatching { classLoader.loadClass(className) }.getOrNull()
                }
                .toList()
        }
    }
}
