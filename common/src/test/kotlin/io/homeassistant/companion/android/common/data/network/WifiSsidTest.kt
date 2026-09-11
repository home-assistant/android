package io.homeassistant.companion.android.common.data.network

import android.net.wifi.WifiManager
import io.homeassistant.companion.android.common.util.SdkVersion
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val ANDROID_10_SDK = 29
private const val ANDROID_11_SDK = 30

class WifiSsidTest {
    @BeforeEach
    fun setUp() {
        SdkVersion.sdkInt = ANDROID_11_SDK
    }

    @AfterEach
    fun tearDown() {
        SdkVersion.resetSdkInt()
    }

    @Test
    fun `Given unknown SSID content on Android 11 then it is unavailable`() {
        val unknownSsid = String(WifiManager.UNKNOWN_SSID.toCharArray())
        assertNotSame(WifiManager.UNKNOWN_SSID, unknownSsid)

        assertTrue(unknownSsid.isUnavailableSsid())
    }

    @Test
    fun `Given unknown SSID content before Android 11 then it is available`() {
        SdkVersion.sdkInt = ANDROID_10_SDK

        assertFalse(String(WifiManager.UNKNOWN_SSID.toCharArray()).isUnavailableSsid())
    }

    @Test
    fun `Given known SSID then it is available`() {
        assertFalse("Home".isUnavailableSsid())
    }
}
