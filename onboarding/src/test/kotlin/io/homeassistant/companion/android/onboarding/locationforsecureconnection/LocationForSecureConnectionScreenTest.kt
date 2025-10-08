package io.homeassistant.companion.android.onboarding.locationforsecureconnection

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.compose.LocationPermissionActivityResultRegistry
import io.homeassistant.companion.android.onboarding.R
import io.homeassistant.companion.android.testing.unit.ConsoleLogTree
import io.homeassistant.companion.android.testing.unit.stringResource
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timber.log.Timber

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class LocationForSecureConnectionScreenTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Before
    fun setup() {
        Timber.plant(ConsoleLogTree)
        ConsoleLogTree.verbose = true
    }

    @Test
    fun `Given selecting most secure connection when next clicked and permission given then go to next screen`() {
        composeTestRule.apply {
            testScreen {
                val nextButton = onNodeWithText(stringResource(R.string.location_secure_connection_next))
                    .performScrollTo()
                    .assertIsDisplayed()
                    .assertIsNotEnabled()

                onNodeWithText(stringResource(R.string.location_secure_connection_most_secure)).performScrollTo().performClick()

                nextButton.assertIsEnabled().performClick()
                assertEquals(false, allowInsecureConnection)
                assertNull(snackbarMessage)
            }
        }
    }

    @Test
    fun `Given selecting less secure connection when next clicked then go to next screen`() {
        composeTestRule.apply {
            testScreen {
                val nextButton = onNodeWithText(stringResource(R.string.location_secure_connection_next))
                    .performScrollTo()
                    .assertIsDisplayed()
                    .assertIsNotEnabled()

                onNodeWithText(stringResource(R.string.location_secure_connection_less_secure)).performScrollTo().performClick()

                nextButton.assertIsEnabled().performClick()
                assertEquals(true, allowInsecureConnection)
                assertNull(snackbarMessage)
            }
        }
    }

    @Test
    fun `Given selecting most secure connection when next clicked and permission not given then stay on screen with snackbar and select less secure`() {
        composeTestRule.apply {
            testScreen(false) {
                val nextButton = onNodeWithText(stringResource(R.string.location_secure_connection_next))
                    .performScrollTo()
                    .assertIsDisplayed()
                    .assertIsNotEnabled()

                onNodeWithText(stringResource(R.string.location_secure_connection_most_secure)).performScrollTo().performClick()

                nextButton.assertIsEnabled().performClick()
                // The callback shouldn't be invoked since the permission is not granted
                assertEquals(null, allowInsecureConnection)
                assertEquals(stringResource(R.string.location_secure_connection_discard_permission), snackbarMessage)

                // reset to make sure the snackbar is not shown again
                snackbarMessage = null

                nextButton.assertIsEnabled().performClick()
                assertEquals(true, allowInsecureConnection)
                assertNull(snackbarMessage)
            }
        }
    }

    private class TestHelper {
        var helpClicked = false
        var allowInsecureConnection: Boolean? = null
        var snackbarMessage: String? = null
    }

    @OptIn(ExperimentalPermissionsApi::class)
    private fun AndroidComposeTestRule<*, *>.testScreen(
        locationPermissionGranted: Boolean = true,
        block: TestHelper.() -> Unit,
    ) {
        TestHelper().apply {
            setContent {
                CompositionLocalProvider(
                    LocalActivityResultRegistryOwner provides object : ActivityResultRegistryOwner {
                        override val activityResultRegistry: ActivityResultRegistry = LocationPermissionActivityResultRegistry(locationPermissionGranted)
                    },
                ) {
                    LocationForSecureConnectionScreen(
                        onHelpClick = { helpClicked = true },
                        onAllowInsecureConnection = { allowInsecureConnection = it },
                        onShowSnackbar = { message, _ ->
                            snackbarMessage = message
                            true
                        },
                    )
                }
            }

            onNodeWithContentDescription(stringResource(commonR.string.get_help)).performClick()
            assertTrue(helpClicked)

            onNodeWithText(stringResource(R.string.location_secure_connection_title)).assertIsDisplayed()
            onNodeWithText(stringResource(R.string.location_secure_connection_content)).assertIsDisplayed()
            onNodeWithText(stringResource(R.string.location_secure_connection_most_secure)).performScrollTo().assertIsDisplayed()
            onNodeWithText(stringResource(R.string.location_secure_connection_less_secure)).performScrollTo().assertIsDisplayed()
            onNodeWithText(stringResource(R.string.location_secure_connection_hint)).performScrollTo().assertIsDisplayed()

            block()
        }
    }
}
