package io.homeassistant.companion.android.testing.unit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * A JUnit 5 extension that sets the Main dispatcher to a test dispatcher before each test and resets it at the end.
 *
 * This extension is useful for testing code that uses the Main dispatcher, such as ViewModel.
 *
 * Example usage:
 * ```kotlin
 * @ExtendWith(MainDispatcherJUnit5Extension::class) // Option 1: Apply to the entire class
 * class MyViewModelTest {
 *
 *     @JvmField
 *     @RegisterExtension // Option 2: Apply as a field
 *     val mainDispatcherExtension = MainDispatcherJUnit5Extension()
 *
 *     private var myViewModel: MyViewModel
 *
 *     @Test
 *     fun `some test using main dispatcher`() = runTest {
 *         // performSomeSuspendAction uses viewModelScope that is going to use the TestDispatcher set by the rule
 *         myViewModel.performSomeSuspendAction()
 *
 *         // Assertions
 *     }
 * }
 * ```
 *
 * @param testDispatcher The [TestDispatcher] to use as the main dispatcher. Defaults to [StandardTestDispatcher].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherJUnit5Extension(val testDispatcher: TestDispatcher = StandardTestDispatcher()) :
    AfterEachCallback,
    BeforeEachCallback {

    override fun beforeEach(context: ExtensionContext) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun afterEach(context: ExtensionContext) {
        Dispatchers.resetMain()
    }
}

/**
 * A JUnit 4 rule that sets the Main dispatcher to a test dispatcher before each test and resets it at the end.
 *
 * This extension is useful for testing code that uses the Main dispatcher, such as ViewModel.
 *
 * Example usage:
 * ```kotlin
 * class MyViewModelTest {
 *
 *     @get:Rule
 *     val mainDispatcherRule = MainDispatcherJUnit4Rule()
 *
 *     private var myViewModel: MyViewModel
 *
 *     @Test
 *     fun testSomething() = runTest {
 *         // performSomeSuspendAction uses viewModelScope that is going to use the TestDispatcher set by the rule
 *         myViewModel.performSomeSuspendAction()
 *
 *         // Assertions
 *     }
 * }
 * ```
 *
 * @param testDispatcher The [TestDispatcher] to use as the main dispatcher. Defaults to [StandardTestDispatcher].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherJUnit4Rule(val testDispatcher: TestDispatcher = StandardTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description?) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description?) {
        Dispatchers.resetMain()
    }
}
