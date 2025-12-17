package io.homeassistant.companion.android.onboarding.sethomenetwork

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.testing.unit.ConsoleLogRule
import io.homeassistant.companion.android.testing.unit.stringResource
import junit.framework.TestCase.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class SetHomeNetworkScreenTest {
    @get:Rule(order = 0)
    var consoleLog = ConsoleLogRule()

    @get:Rule(order = 1)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Test
    fun `Given no vpn and no ethernet when screen is displayed then only show Wi-Fi and handle interactions`() {
        composeTestRule.apply {
            testScreen("", showEthernet = false, showVpn = false) {
                onNodeWithText(stringResource(commonR.string.manage_ssids_vpn)).assertIsNotDisplayed()
                onNodeWithTag(VPN_TAG).assertIsNotDisplayed()
                onNodeWithText(stringResource(commonR.string.manage_ssids_ethernet)).assertIsNotDisplayed()
                onNodeWithTag(ETHERNET_TAG).assertIsNotDisplayed()
                onNodeWithContentDescription(stringResource(commonR.string.clear_text)).assertIsNotDisplayed()

                onNodeWithText(stringResource(commonR.string.set_home_network_next)).performScrollTo().assertIsDisplayed().performClick()
                assertTrue(nextClicked)
            }
        }
    }

    @Test
    fun `Given vpn and no ethernet when screen is displayed then show Wi-Fi and vpn only and handle interactions`() {
        composeTestRule.apply {
            testScreen("", showEthernet = false, showVpn = true) {
                onNodeWithText(stringResource(commonR.string.manage_ssids_vpn)).performScrollTo().assertIsDisplayed()
                onNodeWithTag(VPN_TAG).assertIsDisplayed().performClick()
                assertEquals(false, usingVpnSet)
                onNodeWithText(stringResource(commonR.string.manage_ssids_ethernet)).assertIsNotDisplayed()
                onNodeWithTag(ETHERNET_TAG).assertIsNotDisplayed()
                onNodeWithContentDescription(stringResource(commonR.string.clear_text)).assertIsNotDisplayed()

                onNodeWithText(stringResource(commonR.string.set_home_network_next)).performScrollTo().assertIsDisplayed().performClick()
                assertTrue(nextClicked)
            }
        }
    }

    @Test
    fun `Give ethernet and no vpn when screen is displayed then show Wi-Fi and ethernet only and handle interactions`() {
        composeTestRule.apply {
            testScreen("", showEthernet = true, showVpn = false) {
                onNodeWithText(stringResource(commonR.string.manage_ssids_vpn)).assertIsNotDisplayed()
                onNodeWithTag(VPN_TAG).assertIsNotDisplayed()
                onNodeWithText(stringResource(commonR.string.manage_ssids_ethernet)).performScrollTo().assertIsDisplayed()
                onNodeWithTag(ETHERNET_TAG).assertIsDisplayed().performClick()
                assertEquals(false, usingEthernetSet)
                onNodeWithContentDescription(stringResource(commonR.string.clear_text)).assertIsNotDisplayed()

                onNodeWithText(stringResource(commonR.string.set_home_network_next)).performScrollTo().assertIsDisplayed().performClick()
                assertTrue(nextClicked)
            }
        }
    }

    @Test
    fun `Given screen when setting current wifi network then update currentWifiNetworkSet`() {
        composeTestRule.apply {
            val currentWifiNetwork = "Test"
            testScreen(currentWifiNetwork, showEthernet = true, showVpn = true) {
                onNodeWithText(stringResource(commonR.string.manage_ssids_vpn)).performScrollTo().assertIsDisplayed()
                onNodeWithTag(VPN_TAG).assertIsDisplayed()
                onNodeWithText(stringResource(commonR.string.manage_ssids_ethernet)).performScrollTo().assertIsDisplayed()
                onNodeWithTag(ETHERNET_TAG).assertIsDisplayed()
                onNodeWithContentDescription(stringResource(commonR.string.clear_text)).assertIsDisplayed()
                onNodeWithText(currentWifiNetwork).performScrollTo().assertIsDisplayed().performTextInput("123")
                assertEquals("123$currentWifiNetwork", currentWifiNetworkSet)

                onNodeWithText(stringResource(commonR.string.set_home_network_next)).performScrollTo().assertIsDisplayed().performClick()
                assertTrue(nextClicked)
            }
        }
    }

    private class TestHelper {
        var helpClicked = false
        var nextClicked = false

        var currentWifiNetworkSet: String? = null
        var usingVpnSet: Boolean? = null
        var usingEthernetSet: Boolean? = null
    }

    private fun AndroidComposeTestRule<*, *>.testScreen(
        currentWifiNetwork: String,
        showVpn: Boolean,
        showEthernet: Boolean,
        dsl: TestHelper.() -> Unit = {},
    ) {
        TestHelper().apply {
            setContent {
                SetHomeNetworkScreen(
                    onHelpClick = { helpClicked = true },
                    onNextClick = { nextClicked = true },
                    currentWifiNetwork = currentWifiNetwork,
                    onCurrentWifiNetworkChange = { currentWifiNetworkSet = it },
                    showVpn = showVpn,
                    isUsingVpn = true,
                    onUsingVpnChange = { usingVpnSet = it },
                    showEthernet = showEthernet,
                    isUsingEthernet = true,
                    onUsingEthernetChange = { usingEthernetSet = it },
                )
            }

            onNodeWithContentDescription(stringResource(commonR.string.get_help)).assertIsDisplayed().performClick()
            assertTrue(helpClicked)

            onNodeWithText(stringResource(commonR.string.manage_ssids_warning)).performScrollTo().assertIsDisplayed()

            onNodeWithText(stringResource(commonR.string.set_home_network_title)).performScrollTo().assertIsDisplayed()
            onNodeWithText(stringResource(commonR.string.set_home_network_content)).assertIsDisplayed()
            onNodeWithText(stringResource(commonR.string.set_home_network_wifi_name)).assertIsDisplayed()

            dsl()
        }
    }
}
