package io.homeassistant.companion.android.database.server

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import io.homeassistant.companion.android.common.data.network.NetworkHelper
import io.homeassistant.companion.android.common.data.network.WifiHelper
import io.homeassistant.companion.android.common.util.DisabledLocationHandler
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ServerConnectionInfoTest {
    private val context: Context = mockk(relaxed = true)
    private val wifiHelper: WifiHelper = mockk()
    private val networkHelper: NetworkHelper = mockk()
    private val locationManager: LocationManager = mockk()

    @BeforeEach
    fun setup() {
        mockkStatic(ContextCompat::class)
        mockkObject(DisabledLocationHandler)
        every { context.getSystemService(LocationManager::class.java) } returns locationManager
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    private fun createServerConnectionInfo(
        externalUrl: String = "https://external.example.com",
        internalUrl: String? = null,
        cloudUrl: String? = null,
        internalSsids: List<String> = emptyList(),
        internalEthernet: Boolean? = null,
        internalVpn: Boolean? = null,
    ): ServerConnectionInfo {
        return ServerConnectionInfo(
            externalUrl = externalUrl,
            internalUrl = internalUrl,
            cloudUrl = cloudUrl,
            internalSsids = internalSsids,
            internalEthernet = internalEthernet,
            internalVpn = internalVpn,
        ).apply {
            this.wifiHelper = this@ServerConnectionInfoTest.wifiHelper
            this.networkHelper = this@ServerConnectionInfoTest.networkHelper
        }
    }

    @Test
    fun `Given HTTPS URL when checking security status then returns Secure`() {
        val serverInfo = createServerConnectionInfo()
        val httpsUrl = "https://example.com"

        val result = serverInfo.currentSecurityStatusForUrl(context, httpsUrl)

        assertInstanceOf(SecurityStatus.Secure::class.java, result)
    }

    @Test
    fun `Given HTTP URL when device is on internal network via ethernet then returns Secure`() {
        val serverInfo = createServerConnectionInfo(
            internalUrl = "http://192.168.1.1:8123",
            internalEthernet = true,
        )
        every { networkHelper.isUsingEthernet() } returns true
        val httpUrl = "http://192.168.1.1:8123"

        val result = serverInfo.currentSecurityStatusForUrl(context, httpUrl)

        assertInstanceOf(SecurityStatus.Secure::class.java, result)
    }

    @Test
    fun `Given HTTP URL when device is on internal network via VPN then returns Secure`() {
        val serverInfo = createServerConnectionInfo(
            internalUrl = "http://192.168.1.1:8123",
            internalVpn = true,
        )
        every { networkHelper.isUsingVpn() } returns true
        every { networkHelper.isUsingEthernet() } returns false
        val httpUrl = "http://192.168.1.1:8123"

        val result = serverInfo.currentSecurityStatusForUrl(context, httpUrl)

        assertInstanceOf(SecurityStatus.Secure::class.java, result)
    }

    @Test
    fun `Given HTTP URL when device is on internal network via SSID then returns Secure`() {
        val serverInfo = createServerConnectionInfo(
            internalUrl = "http://192.168.1.1:8123",
            internalSsids = listOf("HomeWiFi"),
        )
        every { networkHelper.isUsingEthernet() } returns false
        every { networkHelper.isUsingVpn() } returns false
        every { wifiHelper.isUsingSpecificWifi(listOf("HomeWiFi")) } returns true
        every { wifiHelper.isUsingWifi() } returns true
        val httpUrl = "http://192.168.1.1:8123"

        val result = serverInfo.currentSecurityStatusForUrl(context, httpUrl)

        assertInstanceOf(SecurityStatus.Secure::class.java, result)
    }

    @Test
    fun `Given HTTP URL when not on internal network and no home setup then returns Insecure with missingHomeSetup true`() {
        val serverInfo = createServerConnectionInfo(
            externalUrl = "http://external.example.com",
            internalUrl = null,
            internalVpn = false,
            internalEthernet = false,
        )
        every {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_GRANTED
        every { DisabledLocationHandler.isLocationEnabled(context) } returns true

        val httpUrl = "http://external.example.com"

        val result = serverInfo.currentSecurityStatusForUrl(context, httpUrl)

        assertInstanceOf(SecurityStatus.Insecure::class.java, result)
        val insecure = result as SecurityStatus.Insecure
        assertTrue(insecure.missingHomeSetup)
        assertFalse(insecure.missingLocation)
    }

    @Test
    fun `Given HTTP URL when not on internal network and has internalSSID then returns Insecure with missingHomeSetup false`() {
        val serverInfo = createServerConnectionInfo(
            externalUrl = "http://external.example.com",
            internalUrl = "http://192.168.1.1:8123",
            internalVpn = false,
            internalEthernet = false,
            internalSsids = listOf("helloworld"),
        )
        every { networkHelper.isUsingEthernet() } returns false
        every { networkHelper.isUsingVpn() } returns false
        every { wifiHelper.isUsingWifi() } returns true
        every { wifiHelper.isUsingSpecificWifi(any()) } returns false
        every {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_GRANTED
        every { DisabledLocationHandler.isLocationEnabled(context) } returns true

        val httpUrl = "http://external.example.com"

        val result = serverInfo.currentSecurityStatusForUrl(context, httpUrl)

        assertInstanceOf(SecurityStatus.Insecure::class.java, result)
        val insecure = result as SecurityStatus.Insecure
        assertFalse(insecure.missingHomeSetup)
        assertFalse(insecure.missingLocation)
    }

    @Test
    fun `Given HTTP URL when not on internal network and VPN enabled then returns Insecure with missingHomeSetup false`() {
        val serverInfo = createServerConnectionInfo(
            externalUrl = "http://external.example.com",
            internalUrl = null,
            internalVpn = true,
            internalEthernet = false,
        )
        every { networkHelper.isUsingVpn() } returns false
        every { networkHelper.isUsingEthernet() } returns false
        every {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_GRANTED
        every { DisabledLocationHandler.isLocationEnabled(context) } returns true

        val httpUrl = "http://external.example.com"

        val result = serverInfo.currentSecurityStatusForUrl(context, httpUrl)

        assertInstanceOf(SecurityStatus.Insecure::class.java, result)
        val insecure = result as SecurityStatus.Insecure
        assertFalse(insecure.missingHomeSetup)
    }

    @Test
    fun `Given HTTP URL when not on internal network and Ethernet enabled then returns Insecure with missingHomeSetup false`() {
        val serverInfo = createServerConnectionInfo(
            externalUrl = "http://external.example.com",
            internalUrl = null,
            internalVpn = false,
            internalEthernet = true,
        )
        every { networkHelper.isUsingEthernet() } returns false
        every {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_GRANTED
        every { DisabledLocationHandler.isLocationEnabled(context) } returns true

        val httpUrl = "http://external.example.com"

        val result = serverInfo.currentSecurityStatusForUrl(context, httpUrl)

        assertInstanceOf(SecurityStatus.Insecure::class.java, result)
        val insecure = result as SecurityStatus.Insecure
        assertFalse(insecure.missingHomeSetup)
    }

    @Test
    fun `Given HTTP URL when location permission denied then returns Insecure with missingLocation true`() {
        val serverInfo = createServerConnectionInfo(
            externalUrl = "http://external.example.com",
            internalUrl = "http://192.168.1.1:8123",
            internalVpn = false,
            internalEthernet = false,
        )
        every { networkHelper.isUsingEthernet() } returns false
        every { networkHelper.isUsingVpn() } returns false
        every { wifiHelper.isUsingSpecificWifi(any()) } returns false
        every {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_DENIED
        every { DisabledLocationHandler.isLocationEnabled(context) } returns true

        val httpUrl = "http://external.example.com"

        val result = serverInfo.currentSecurityStatusForUrl(context, httpUrl)

        assertInstanceOf(SecurityStatus.Insecure::class.java, result)
        val insecure = result as SecurityStatus.Insecure
        assertTrue(insecure.missingLocation)
    }

    @Test
    fun `Given HTTP URL when location services disabled then returns Insecure with missingLocation true`() {
        val serverInfo = createServerConnectionInfo(
            externalUrl = "http://external.example.com",
            internalUrl = "http://192.168.1.1:8123",
            internalVpn = false,
            internalEthernet = false,
        )
        every { networkHelper.isUsingEthernet() } returns false
        every { networkHelper.isUsingVpn() } returns false
        every { wifiHelper.isUsingSpecificWifi(any()) } returns false
        every {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_GRANTED
        every { DisabledLocationHandler.isLocationEnabled(context) } returns false

        val httpUrl = "http://external.example.com"

        val result = serverInfo.currentSecurityStatusForUrl(context, httpUrl)

        assertInstanceOf(SecurityStatus.Insecure::class.java, result)
        val insecure = result as SecurityStatus.Insecure
        assertTrue(insecure.missingLocation)
    }

    @Test
    fun `Given HTTP URL when both location permission denied and services disabled then returns Insecure with missingLocation true`() {
        val serverInfo = createServerConnectionInfo(
            externalUrl = "http://external.example.com",
            internalUrl = "http://192.168.1.1:8123",
            internalVpn = false,
            internalEthernet = false,
        )
        every { networkHelper.isUsingEthernet() } returns false
        every { networkHelper.isUsingVpn() } returns false
        every { wifiHelper.isUsingSpecificWifi(any()) } returns false
        every {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_DENIED
        every { DisabledLocationHandler.isLocationEnabled(context) } returns false

        val httpUrl = "http://external.example.com"

        val result = serverInfo.currentSecurityStatusForUrl(context, httpUrl)

        assertInstanceOf(SecurityStatus.Insecure::class.java, result)
        val insecure = result as SecurityStatus.Insecure
        assertTrue(insecure.missingLocation)
    }

    @Test
    fun `Given HTTP URL when both missingHomeSetup and missingLocation conditions met then returns Insecure with both flags true`() {
        val serverInfo = createServerConnectionInfo(
            externalUrl = "http://external.example.com",
            internalUrl = null,
            internalVpn = false,
            internalEthernet = false,
        )
        every {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_DENIED
        every { DisabledLocationHandler.isLocationEnabled(context) } returns false

        val httpUrl = "http://external.example.com"

        val result = serverInfo.currentSecurityStatusForUrl(context, httpUrl)

        assertInstanceOf(SecurityStatus.Insecure::class.java, result)
        val insecure = result as SecurityStatus.Insecure
        assertTrue(insecure.missingHomeSetup)
        assertTrue(insecure.missingLocation)
    }

    @Test
    fun `Given HTTP URL with null internalVpn and internalEthernet when checking security then missingHomeSetup is true`() {
        val serverInfo = createServerConnectionInfo(
            externalUrl = "http://external.example.com",
            internalUrl = null,
            internalVpn = null,
            internalEthernet = null,
        )
        every {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_GRANTED
        every { DisabledLocationHandler.isLocationEnabled(context) } returns true

        val httpUrl = "http://external.example.com"

        val result = serverInfo.currentSecurityStatusForUrl(context, httpUrl)

        assertInstanceOf(SecurityStatus.Insecure::class.java, result)
        val insecure = result as SecurityStatus.Insecure
        assertTrue(insecure.missingHomeSetup)
    }
}
