package io.homeassistant.companion.android.frontend.barcode.ui

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class BarcodeScannerTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltComponentActivity>()

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val cancelDescription = context.getString(commonR.string.cancel)
    private val flashlightDescription = context.getString(commonR.string.toggle_flashlight)
    private val permissionTitle = context.getString(commonR.string.barcode_camera_permission_title)
    private val permissionAction = context.getString(commonR.string.barcode_camera_permission_action)

    /** Renders [BarcodeScannerContent] with permission granted, skipping the real camera. */
    private fun setGrantedContent(
        title: String = "Scan a code",
        description: String = "Point at the barcode",
        alternativeOptionLabel: String? = null,
        hasFlashlight: Boolean = true,
        flashlightOn: Boolean = false,
        onToggleFlashlight: () -> Unit = {},
        onCancel: (Boolean) -> Unit = {},
    ) {
        setContent(
            hasCameraPermission = true,
            inspection = true,
            title = title,
            description = description,
            alternativeOptionLabel = alternativeOptionLabel,
            hasFlashlight = hasFlashlight,
            flashlightOn = flashlightOn,
            onToggleFlashlight = onToggleFlashlight,
            onCancel = onCancel,
        )
    }

    /** Renders [BarcodeScannerContent] with permission denied (the rationale state). */
    private fun setDeniedContent(onRequestPermission: () -> Unit = {}, onCancel: (Boolean) -> Unit = {}) {
        setContent(
            hasCameraPermission = false,
            hasFlashlight = true,
            inspection = false,
            onRequestPermission = onRequestPermission,
            onCancel = onCancel,
        )
    }

    private fun setContent(
        hasCameraPermission: Boolean,
        inspection: Boolean,
        title: String = "Scan a code",
        description: String = "Point at the barcode",
        alternativeOptionLabel: String? = null,
        hasFlashlight: Boolean = true,
        flashlightOn: Boolean = false,
        onRequestPermission: () -> Unit = {},
        onToggleFlashlight: () -> Unit = {},
        onCancel: (Boolean) -> Unit = {},
    ) {
        composeRule.setContent {
            val content: @Composable () -> Unit = {
                BarcodeScannerContent(
                    title = title,
                    description = description,
                    alternativeOptionLabel = alternativeOptionLabel,
                    hasCameraPermission = hasCameraPermission,
                    hasFlashlight = hasFlashlight,
                    flashlightOn = flashlightOn,
                    onRequestPermission = onRequestPermission,
                    onResult = { _, _ -> },
                    onToggleFlashlight = onToggleFlashlight,
                    onCancel = onCancel,
                )
            }
            if (inspection) {
                CompositionLocalProvider(LocalInspectionMode provides true) { content() }
            } else {
                content()
            }
        }
    }

    @Test
    fun `Given permission granted and alternativeOptionLabel set then action button is displayed`() {
        setGrantedContent(alternativeOptionLabel = "Enter manually")

        composeRule.onNodeWithText("Enter manually").assertIsDisplayed()
    }

    @Test
    fun `Given permission granted and title and description then both are displayed`() {
        setGrantedContent(title = "Scan a Matter QR code", description = "Hold the device steady")

        composeRule.onNodeWithText("Scan a Matter QR code").assertIsDisplayed()
        composeRule.onNodeWithText("Hold the device steady").assertIsDisplayed()
    }

    @Test
    fun `Given permission granted when close icon tapped then onCancel is called with false`() {
        val cancelCalls = mutableListOf<Boolean>()
        setGrantedContent(onCancel = { forAction -> cancelCalls += forAction })

        composeRule.onNodeWithContentDescription(cancelDescription).performClick()

        assertEquals(listOf(false), cancelCalls)
    }

    @Test
    fun `Given permission granted when alternative action button tapped then onCancel is called with true`() {
        val cancelCalls = mutableListOf<Boolean>()
        setGrantedContent(alternativeOptionLabel = "Enter manually", onCancel = { forAction -> cancelCalls += forAction })

        composeRule.onNodeWithText("Enter manually").performClick()

        assertEquals(listOf(true), cancelCalls)
    }

    @Test
    fun `Given permission granted and device has flashlight then flashlight button is displayed`() {
        setGrantedContent(hasFlashlight = true)

        composeRule.onNodeWithContentDescription(flashlightDescription).assertIsDisplayed()
    }

    @Test
    fun `Given permission granted and device has no flashlight then flashlight button is not displayed`() {
        setGrantedContent(hasFlashlight = false)

        composeRule.onAllNodesWithContentDescription(flashlightDescription).assertCountEquals(0)
    }

    @Test
    fun `Given permission granted when flashlight button tapped then onToggleFlashlight is called`() {
        var toggleCount = 0
        setGrantedContent(hasFlashlight = true, onToggleFlashlight = { toggleCount++ })

        composeRule.onNodeWithContentDescription(flashlightDescription).performClick()

        assertEquals(1, toggleCount)
    }

    @Test
    fun `Given permission denied when rendered then rationale and action button are displayed`() {
        setDeniedContent()

        composeRule.onNodeWithText(permissionTitle).assertIsDisplayed()
        composeRule.onNodeWithText(permissionAction).assertIsDisplayed()
        // No flashlight FAB while permission is missing (there is no camera to light).
        composeRule.onAllNodesWithContentDescription(flashlightDescription).assertCountEquals(0)
    }

    @Test
    fun `Given permission denied when action button tapped then onRequestPermission is called`() {
        var requestCount = 0
        setDeniedContent(onRequestPermission = { requestCount++ })

        composeRule.onNodeWithText(permissionAction).performClick()

        assertEquals(1, requestCount)
    }

    @Test
    fun `Given permission denied when close icon tapped then onCancel is called with false`() {
        val cancelCalls = mutableListOf<Boolean>()
        setDeniedContent(onCancel = { forAction -> cancelCalls += forAction })

        composeRule.onNodeWithContentDescription(cancelDescription).performClick()

        assertEquals(listOf(false), cancelCalls)
    }
}
