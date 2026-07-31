package io.homeassistant.companion.android.changelog.navigation

import android.content.Context
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
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.frontend.navigation.FrontendRoute
import io.homeassistant.companion.android.frontend.navigation.FrontendTarget
import io.homeassistant.companion.android.testing.unit.stringResource
import javax.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val FRONTEND_FAKE_CONTENT = "frontend"

/** Prefs file/key the seen state is persisted in, see [io.homeassistant.companion.android.changelog.ChangelogRepository]. */
private const val PREFERENCES_NAME = "changelog"
private const val VERSION_KEY = "ChangeLog_last_version_code"

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
internal class ChangelogNavigationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Inject
    lateinit var prefsRepository: PrefsRepository

    private lateinit var navController: TestNavHostController

    @Before
    fun setup() {
        hiltRule.inject()
    }

    private fun seedLastSeenVersionCode(versionCode: Int) {
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(VERSION_KEY, versionCode)
            .commit()
    }

    private fun lastSeenVersionCode(): Int = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        .getInt(VERSION_KEY, -1)

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
    fun `Given unseen changelog when frontend is displayed then navigates to changelog and marks it seen`() {
        seedLastSeenVersionCode(BuildConfig.VERSION_CODE - 1)

        setContent()
        composeTestRule.waitUntil { isOnChangelog() }

        composeTestRule.onNodeWithText(composeTestRule.stringResource(commonR.string.changelog_screen_title))
            .assertIsDisplayed()
        composeTestRule.waitUntil { lastSeenVersionCode() == BuildConfig.VERSION_CODE }
    }

    @Test
    fun `Given changelog already seen when frontend is displayed then stays on frontend`() {
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
        assertEquals(BuildConfig.VERSION_CODE - 1, lastSeenVersionCode())
    }

    @Test
    fun `Given changelog displayed when closing then returns to frontend`() {
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
