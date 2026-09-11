package io.homeassistant.companion.android.frontend.improv.ui

import android.net.wifi.WifiManager
import android.os.Build
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.frontend.improv.ImprovUIState
import io.homeassistant.companion.android.testing.unit.stringResource
import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class ImprovSheetTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @After
    fun tearDown() {
        SdkVersion.resetSdkInt()
    }

    @Test
    fun `Given unknown SSID content on Android 11 when configuring then SSID input is empty`() {
        val unknownSsid = String(WifiManager.UNKNOWN_SSID.toCharArray())
        assertNotSame(WifiManager.UNKNOWN_SSID, unknownSsid)
        assertPrefilledSsid(unknownSsid, "")
    }

    @Test
    fun `Given known SSID when configuring then SSID is prefilled`() {
        assertPrefilledSsid("Home", "Home")
    }

    private fun assertPrefilledSsid(ssid: String?, expected: String) {
        SdkVersion.sdkInt = Build.VERSION_CODES.R
        composeTestRule.setContent {
            HAThemeForPreview {
                ImprovSheet(
                    screenState = ImprovUIState.ConfiguringDevice(
                        deviceName = "Device",
                        deviceAddress = "aa:bb:cc:dd:ee:ff",
                        activeSsid = ssid,
                    ),
                    onConnect = { _, _ -> },
                    onRestart = {},
                    onDismiss = {},
                )
            }
        }
        val ssidLabel = composeTestRule.stringResource(commonR.string.improv_wifi_ssid)
        composeTestRule.onNode(hasSetTextAction() and hasText(ssidLabel))
            .assertTextEquals(ssidLabel, expected)
    }
}
