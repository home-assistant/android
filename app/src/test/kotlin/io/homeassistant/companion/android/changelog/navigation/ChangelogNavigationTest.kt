package io.homeassistant.companion.android.changelog.navigation

import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.testing.TestNavHostController
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.frontend.navigation.FrontendRoute
import io.homeassistant.companion.android.frontend.navigation.FrontendTarget
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit4Rule
import io.homeassistant.companion.android.testing.unit.stringResource
import javax.inject.Inject
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val FRONTEND_FAKE_CONTENT = "frontend"

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
internal class ChangelogNavigationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @get:Rule(order = 2)
    val mainDispatcherRule = MainDispatcherJUnit4Rule()

    @Inject
    lateinit var prefsRepository: PrefsRepository

    private lateinit var navController: TestNavHostController

    @Before
    fun setup() {
        hiltRule.inject()
    }

    private suspend fun seedLastSeenVersionCode(versionCode: Int) {
        prefsRepository.markChangelogSeen(versionCode)
    }

    private suspend fun hasUnseenChangelog(): Boolean = prefsRepository.wasAppUpdatedSinceChangelogSeen(BuildConfig.VERSION_CODE)

    /**
     * Runs the scheduler until the changelog is marked seen. The ViewModel coroutine runs on the
     * controlled Main dispatcher, but the write itself hops to the real IO dispatcher, so keep
     * advancing until its result is visible. Bounded by the runTest timeout.
     */
    private suspend fun TestScope.awaitChangelogMarkedSeen() {
        while (hasUnseenChangelog()) {
            advanceUntilIdle()
        }
    }

    private fun setContent() {
        composeTestRule.setContent {
            navController = TestNavHostController(LocalContext.current)
            navController.navigatorProvider.addNavigator(ComposeNavigator())

            NavHost(
                navController = navController,
                startDestination = FrontendRoute(FrontendTarget.Default),
            ) {
                composable<FrontendRoute> { Text(FRONTEND_FAKE_CONTENT) }
                changelogScreen(navController, onOpenUrl = {})
            }

            ChangelogAutoShowEffect(navController)
        }
    }

    private fun isOnChangelog(): Boolean = navController.currentBackStackEntry?.destination?.hasRoute<ChangelogRoute>() == true

    @Test
    fun `Given unseen changelog when frontend is displayed then navigates to changelog and marks it seen`() = runTest {
        seedLastSeenVersionCode(BuildConfig.VERSION_CODE - 1)

        setContent()
        composeTestRule.waitUntil { isOnChangelog() }

        composeTestRule.onNodeWithText(composeTestRule.stringResource(commonR.string.changelog_screen_title))
            .assertIsDisplayed()
        awaitChangelogMarkedSeen()
    }

    @Test
    fun `Given changelog already seen when frontend is displayed then stays on frontend`() = runTest {
        seedLastSeenVersionCode(BuildConfig.VERSION_CODE)

        setContent()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(FRONTEND_FAKE_CONTENT).assertIsDisplayed()
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<FrontendRoute>() == true)
    }

    @Test
    fun `Given popup disabled when frontend is displayed then stays on frontend without marking seen`() = runTest {
        seedLastSeenVersionCode(BuildConfig.VERSION_CODE - 1)
        prefsRepository.setChangeLogPopupEnabled(false)

        setContent()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(FRONTEND_FAKE_CONTENT).assertIsDisplayed()
        assertTrue(hasUnseenChangelog())
    }

    @Test
    fun `Given changelog displayed when closing then returns to frontend`() = runTest {
        seedLastSeenVersionCode(BuildConfig.VERSION_CODE - 1)

        setContent()
        composeTestRule.waitUntil { isOnChangelog() }

        composeTestRule.onNodeWithText(composeTestRule.stringResource(commonR.string.changelog_got_it))
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(FRONTEND_FAKE_CONTENT).assertIsDisplayed()
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<FrontendRoute>() == true)
    }
}
