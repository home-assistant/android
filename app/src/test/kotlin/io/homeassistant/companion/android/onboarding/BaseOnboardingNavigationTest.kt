package io.homeassistant.companion.android.onboarding

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidRule
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.util.FakePermissionResultRegistry
import io.homeassistant.companion.android.util.compose.navigateToUri
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule

// Robolectric leaves Settings.Secure.ANDROID_ID null, but the integration graph injects it as a
// non-null @NamedDeviceId, so we seed a value to avoid a null-from-@Provides crash during DI.
private const val FAKE_ANDROID_ID = "robolectric-android-id"

/**
 * Base class for onboarding navigation tests providing shared setup, mocks, and utilities.
 *
 * Subclasses should extend this class and use [testNavigation] to set up the navigation
 * test environment with the onboarding flow.
 */
internal abstract class BaseOnboardingNavigationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    protected lateinit var navController: TestNavHostController

    protected var onboardingDone = false

    @Before
    fun baseSetup() {
        Settings.Secure.putString(
            ApplicationProvider.getApplicationContext<Context>().contentResolver,
            Settings.Secure.ANDROID_ID,
            FAKE_ANDROID_ID,
        )
        mockkStatic(NavController::navigateToUri)
        coEvery { any<NavController>().navigateToUri(any(), any()) } just Runs
    }

    protected fun setContent(
        urlToOnboard: String? = null,
        hideExistingServers: Boolean = false,
        skipWelcome: Boolean = false,
        hasLocationTracking: Boolean = true,
        fromInvitation: Boolean = false,
        permissionResultRegistry: ActivityResultRegistry = FakePermissionResultRegistry(grantAll = true),
    ) {
        composeTestRule.setContent {
            navController = TestNavHostController(LocalContext.current)
            navController.navigatorProvider.addNavigator(ComposeNavigator())

            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides object : ActivityResultRegistryOwner {
                    override val activityResultRegistry: ActivityResultRegistry = permissionResultRegistry
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
                        fromInvitation = fromInvitation,
                    )
                }
            }
        }
    }

    protected open fun testNavigation(
        urlToOnboard: String? = null,
        hideExistingServers: Boolean = false,
        skipWelcome: Boolean = false,
        hasLocationTracking: Boolean = true,
        fromInvitation: Boolean = false,
        testContent: suspend AndroidComposeTestRule<*, *>.() -> Unit,
    ) {
        setContent(
            urlToOnboard = urlToOnboard,
            hideExistingServers = hideExistingServers,
            skipWelcome = skipWelcome,
            hasLocationTracking = hasLocationTracking,
            fromInvitation = fromInvitation,
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
