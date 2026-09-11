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
    fun `Given unknown SSID constant on Android 11 then it is unavailable`() {
        assertTrue(isUnavailableSsid(WifiManager.UNKNOWN_SSID))
    }

    @Test
    fun `Given distinct unknown SSID content on Android 11 then it is available`() {
        val unknownSsid = String(WifiManager.UNKNOWN_SSID.toCharArray())
        assertNotSame(WifiManager.UNKNOWN_SSID, unknownSsid)

        assertFalse(isUnavailableSsid(unknownSsid))
    }

    @Test
    fun `Given unknown SSID content before Android 11 then it is available`() {
        SdkVersion.sdkInt = ANDROID_10_SDK

        assertFalse(isUnavailableSsid(String(WifiManager.UNKNOWN_SSID.toCharArray())))
    }
}
