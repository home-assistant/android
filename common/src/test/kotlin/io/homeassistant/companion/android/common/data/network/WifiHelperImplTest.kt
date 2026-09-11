package io.homeassistant.companion.android.common.data.network

import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import io.homeassistant.companion.android.common.util.SdkVersion
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val ANDROID_11_SDK = 30

class WifiHelperImplTest {
    private val wifiInfo = mockk<WifiInfo>()
    private val wifiManager = mockk<WifiManager>()
    private val helper = WifiHelperImpl(mockk(), wifiManager)

    @BeforeEach
    fun setUp() {
        SdkVersion.sdkInt = ANDROID_11_SDK
        every { wifiManager.connectionInfo } returns wifiInfo
        every { wifiInfo.bssid } returns null
    }

    @AfterEach
    fun tearDown() {
        SdkVersion.resetSdkInt()
    }

    @Test
    fun `Given unknown SSID when checking networks then it does not match`() {
        every { wifiInfo.ssid } returns WifiManager.UNKNOWN_SSID

        assertFalse(helper.isUsingSpecificWifi(listOf(WifiManager.UNKNOWN_SSID)))
    }

    @Test
    fun `Given real network named unknown SSID when checking networks then it matches`() {
        every { wifiInfo.ssid } returns "\"${WifiManager.UNKNOWN_SSID}\""

        assertTrue(helper.isUsingSpecificWifi(listOf(WifiManager.UNKNOWN_SSID)))
    }

    @Test
    fun `Given unknown SSID when BSSID matches then network still matches`() {
        every { wifiInfo.ssid } returns WifiManager.UNKNOWN_SSID
        every { wifiInfo.bssid } returns "aa:bb:cc:dd:ee:ff"

        assertFalse(helper.isUsingSpecificWifi(listOf(WifiManager.UNKNOWN_SSID)))
        assertTrue(helper.isUsingSpecificWifi(listOf("${WifiHelper.BSSID_PREFIX}AA:BB:CC:DD:EE:FF")))
    }
}
