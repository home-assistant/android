package io.homeassistant.companion.android.onboarding

import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.testing.TestNavHostController
import dagger.hilt.android.testing.HiltAndroidRule
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.testing.unit.ConsoleLogRule
import io.homeassistant.companion.android.util.LocationPermissionActivityResultRegistry
import io.homeassistant.companion.android.util.compose.navigateToUri
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule

/**
 * Base class for onboarding navigation tests providing shared setup, mocks, and utilities.
 *
 * Subclasses should extend this class and use [testNavigation] to set up the navigation
 * test environment with the onboarding flow.
 */
internal abstract class BaseOnboardingNavigationTest {

    @get:Rule(order = 0)
    var consoleLog = ConsoleLogRule()

    @get:Rule(order = 1)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    protected lateinit var navController: TestNavHostController

    protected var onboardingDone = false

    @Before
    fun baseSetup() {
        mockkStatic(NavController::navigateToUri)
        coEvery { any<NavController>().navigateToUri(any(), any()) } just Runs
    }

    protected fun setContent(
        urlToOnboard: String? = null,
        hideExistingServers: Boolean = false,
        skipWelcome: Boolean = false,
        hasLocationTracking: Boolean = true,
    ) {
        composeTestRule.setContent {
            navController = TestNavHostController(LocalContext.current)
            navController.navigatorProvider.addNavigator(ComposeNavigator())

            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides object : ActivityResultRegistryOwner {
                    override val activityResultRegistry: ActivityResultRegistry =
                        LocationPermissionActivityResultRegistry(true)
                },
            ) {
                NavHost(
                    navController = navController,
                    startDestination = OnboardingRoute(hasLocationTracking = true),
                ) {
                    onboarding(
                        navController,
                        onShowSnackbar = { _, _ -> true },
                        onOnboardingDone = {
                            onboardingDone = true
                        },
                        urlToOnboard = urlToOnboard,
                        hideExistingServers = hideExistingServers,
                        skipWelcome = skipWelcome,
                        hasLocationTracking = hasLocationTracking,
                    )
                }
            }
        }
    }

    protected fun testNavigation(
        urlToOnboard: String? = null,
        hideExistingServers: Boolean = false,
        skipWelcome: Boolean = false,
        hasLocationTracking: Boolean = true,
        testContent: suspend AndroidComposeTestRule<*, *>.() -> Unit,
    ) {
        setContent(
            urlToOnboard = urlToOnboard,
            hideExistingServers = hideExistingServers,
            skipWelcome = skipWelcome,
            hasLocationTracking = hasLocationTracking,
        )
        runTest {
            composeTestRule.testContent()
        }
    }

    protected fun mockCheckPermission(grant: Boolean) {
        mockkStatic(ContextCompat::class)
        every {
            ContextCompat.checkSelfPermission(any(), any())
        } returns if (grant) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
    }
}
