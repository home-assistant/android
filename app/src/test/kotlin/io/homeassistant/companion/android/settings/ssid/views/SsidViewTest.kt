package io.homeassistant.companion.android.settings.ssid.views

import android.net.wifi.WifiManager
import android.os.Build
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.util.SdkVersion
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
class SsidViewTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @After
    fun tearDown() {
        SdkVersion.resetSdkInt()
    }

    @Test
    fun `Given known SSID when showing networks then suggestion is shown`() {
        assertSsidSuggestion("Home", expected = true)
    }

    @Test
    fun `Given unknown SSID content when showing networks then suggestion is hidden`() {
        val unknownSsid = String(WifiManager.UNKNOWN_SSID.toCharArray())
        assertNotSame(WifiManager.UNKNOWN_SSID, unknownSsid)

        assertSsidSuggestion(unknownSsid, expected = false)
    }

    @Test
    fun `Given already configured SSID when showing networks then suggestion is hidden`() {
        assertSsidSuggestion("Home", expected = false, wifiSsids = listOf("Home"))
    }

    private fun assertSsidSuggestion(
        ssid: String?,
        expected: Boolean,
        wifiSsids: List<String> = emptyList(),
    ) {
        SdkVersion.sdkInt = Build.VERSION_CODES.R
        composeTestRule.setContent {
            HAThemeForPreview {
                SsidView(
                    wifiSsids = wifiSsids,
                    canReadWifi = true,
                    ethernet = null,
                    vpn = null,
                    prioritizeInternal = false,
                    usingWifi = true,
                    activeSsid = ssid,
                    activeBssid = null,
                    onAddWifiSsid = { true },
                    onRemoveWifiSsid = {},
                    onRequestPermission = {},
                    onSetEthernet = {},
                    onSetVpn = {},
                    onSetPrioritize = {},
                )
            }
        }
        val suggestion = composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(commonR.string.add_ssid_name_suggestion, ssid),
        )
        if (expected) suggestion.assertExists() else suggestion.assertDoesNotExist()
    }
}
