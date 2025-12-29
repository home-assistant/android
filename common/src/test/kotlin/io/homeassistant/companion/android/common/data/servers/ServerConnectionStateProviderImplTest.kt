package io.homeassistant.companion.android.common.data.servers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import androidx.core.content.ContextCompat
import io.homeassistant.companion.android.common.data.network.NetworkHelper
import io.homeassistant.companion.android.common.data.network.WifiHelper
import io.homeassistant.companion.android.common.util.DisabledLocationHandler
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerDao
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource

@ExtendWith(ConsoleLogExtension::class)
class ServerConnectionStateProviderImplTest {
    private val context: Context = mockk(relaxed = true)
    private val wifiHelper: WifiHelper = mockk()
    private val networkHelper: NetworkHelper = mockk()
    private val serverManager: ServerManager = mockk()
    private val serverDao: ServerDao = mockk()
    private val connectivityManager: ConnectivityManager = mockk(relaxed = true)
    private val locationManager: LocationManager = mockk()

    private val serverId = 1

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

    private fun createServerConnectionStateProvider(
        externalUrl: String = "https://external.example.com",
        internalUrl: String? = null,
        cloudUrl: String? = null,
        cloudhookUrl: String? = null,
        webhookId: String? = null,
        useCloud: Boolean = false,
        internalSsids: List<String> = emptyList(),
        internalEthernet: Boolean? = null,
        internalVpn: Boolean? = null,
        prioritizeInternal: Boolean = false,
        allowInsecureConnection: Boolean? = null,
    ): ServerConnectionStateProviderImpl {
        val connection = ServerConnectionInfo(
            externalUrl = externalUrl,
            internalUrl = internalUrl,
            cloudUrl = cloudUrl,
            cloudhookUrl = cloudhookUrl,
            webhookId = webhookId,
            useCloud = useCloud,
            internalSsids = internalSsids,
            internalEthernet = internalEthernet,
            internalVpn = internalVpn,
            prioritizeInternal = prioritizeInternal,
            allowInsecureConnection = allowInsecureConnection,
        )
        val server = Server(
            id = serverId,
            _name = "Test Server",
            nameOverride = null,
            _version = "2024.1.0",
            deviceRegistryId = null,
            listOrder = 0,
            deviceName = null,
            connection = connection,
            session = ServerSessionInfo(),
            user = ServerUserInfo(),
        )
        coEvery { serverManager.getServer(serverId) } returns server

        return ServerConnectionStateProviderImpl(
            context = context,
            serverManager = serverManager,
            serverDao = serverDao,
            wifiHelper = wifiHelper,
            networkHelper = networkHelper,
            connectivityManager = connectivityManager,
            serverId = serverId,
        )
    }

    @Nested
    inner class IsInternal {

        @Test
        fun `Given requiresUrl true and no internal URL when calling isInternal then returns false`() = runTest {
            val provider = createServerConnectionStateProvider(
                externalUrl = "https://external.example.com",
                internalUrl = null,
                internalEthernet = true,
            )
            every { networkHelper.isUsingEthernet() } returns true

            assertFalse(provider.isInternal(requiresUrl = true))
        }

        @Test
        fun `Given requiresUrl false and no internal URL but using ethernet when calling isInternal then checks network state and returns true`() = runTest {
            val provider = createServerConnectionStateProvider(
                externalUrl = "https://external.example.com",
                internalUrl = null,
                internalEthernet = true,
            )
            every { networkHelper.isUsingEthernet() } returns true

            assertTrue(provider.isInternal(requiresUrl = false))
        }

        @Test
        fun `Given ethernet enabled and using ethernet when calling isInternal then returns true`() = runTest {
            val provider = createServerConnectionStateProvider(
                externalUrl = "https://external.example.com",
                internalUrl = "http://192.168.1.1:8123",
                internalEthernet = true,
            )
            every { networkHelper.isUsingEthernet() } returns true

            assertTrue(provider.isInternal())
        }

        @Test
        fun `Given VPN enabled and using VPN when calling isInternal then returns true`() = runTest {
            val provider = createServerConnectionStateProvider(
                externalUrl = "https://external.example.com",
                internalUrl = "http://192.168.1.1:8123",
                internalVpn = true,
            )
            every { networkHelper.isUsingVpn() } returns true

            assertTrue(provider.isInternal())
        }

        @Test
        fun `Given SSID configured and using matching WiFi when calling isInternal then returns true`() = runTest {
            val provider = createServerConnectionStateProvider(
                externalUrl = "https://external.example.com",
                internalUrl = "http://192.168.1.1:8123",
                internalSsids = listOf("HomeWiFi"),
            )
            every { wifiHelper.isUsingSpecificWifi(listOf("HomeWiFi")) } returns true
            every { wifiHelper.isUsingWifi() } returns true

            assertTrue(provider.isInternal())
        }

        @Test
        fun `Given SSID configured but not using WiFi when calling isInternal then returns false`() = runTest {
            val provider = createServerConnectionStateProvider(
                externalUrl = "https://external.example.com",
                internalUrl = "http://192.168.1.1:8123",
                internalSsids = listOf("HomeWiFi"),
            )
            every { wifiHelper.isUsingSpecificWifi(listOf("HomeWiFi")) } returns true
            every { wifiHelper.isUsingWifi() } returns false

            assertFalse(provider.isInternal())
        }

        @Test
        fun `Given no home network configuration when calling isInternal then returns false`() = runTest {
            val provider = createServerConnectionStateProvider(
                externalUrl = "https://external.example.com",
                internalUrl = "http://192.168.1.1:8123",
            )

            assertFalse(provider.isInternal())
        }
    }

    @Nested
    inner class GetSecurityState {

        @Test
        fun `Given device is on home network via ethernet then isOnHomeNetwork returns true`() = runTest {
            val repository = createServerConnectionStateProvider(
                externalUrl = "http://external.example.com",
                internalUrl = "http://192.168.1.1:8123",
                internalEthernet = true,
            )
            every { networkHelper.isUsingEthernet() } returns true
            every {
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            } returns PackageManager.PERMISSION_GRANTED
            every { DisabledLocationHandler.isLocationEnabled(context) } returns true

            val result = repository.getSecurityState()

            assertTrue(result.isOnHomeNetwork)
            assertTrue(result.hasHomeSetup)
            assertTrue(result.locationEnabled)
        }

        @Test
        fun `Given device is on home network via VPN then isOnHomeNetwork returns true`() = runTest {
            val repository = createServerConnectionStateProvider(
                externalUrl = "http://external.example.com",
                internalUrl = "http://192.168.1.1:8123",
                internalVpn = true,
            )
            every { networkHelper.isUsingVpn() } returns true
            every { networkHelper.isUsingEthernet() } returns false
            every {
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            } returns PackageManager.PERMISSION_GRANTED
            every { DisabledLocationHandler.isLocationEnabled(context) } returns true

            val result = repository.getSecurityState()

            assertTrue(result.isOnHomeNetwork)
        }

        @Test
        fun `Given device is on home network via SSID then isOnHomeNetwork returns true`() = runTest {
            val repository = createServerConnectionStateProvider(
                externalUrl = "http://external.example.com",
                internalUrl = "http://192.168.1.1:8123",
                internalSsids = listOf("HomeWiFi"),
            )
            every { networkHelper.isUsingEthernet() } returns false
            every { networkHelper.isUsingVpn() } returns false
            every { wifiHelper.isUsingSpecificWifi(listOf("HomeWiFi")) } returns true
            every { wifiHelper.isUsingWifi() } returns true
            every {
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            } returns PackageManager.PERMISSION_GRANTED
            every { DisabledLocationHandler.isLocationEnabled(context) } returns true

            val result = repository.getSecurityState()

            assertTrue(result.isOnHomeNetwork)
        }

        @Test
        fun `Given not on home network and no home setup then hasHomeSetup is false`() = runTest {
            val repository = createServerConnectionStateProvider(
                externalUrl = "http://external.example.com",
                internalUrl = null,
                internalVpn = false,
                internalEthernet = false,
            )
            every {
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            } returns PackageManager.PERMISSION_GRANTED
            every { DisabledLocationHandler.isLocationEnabled(context) } returns true

            val result = repository.getSecurityState()

            assertFalse(result.isOnHomeNetwork)
            assertFalse(result.hasHomeSetup)
            assertTrue(result.locationEnabled)
        }

        @Test
        fun `Given not on home network and has internalSSID then hasHomeSetup is true`() = runTest {
            val repository = createServerConnectionStateProvider(
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

            val result = repository.getSecurityState()

            assertFalse(result.isOnHomeNetwork)
            assertTrue(result.hasHomeSetup)
            assertTrue(result.locationEnabled)
        }

        @Test
        fun `Given not on home network and VPN enabled then hasHomeSetup is true`() = runTest {
            val repository = createServerConnectionStateProvider(
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

            val result = repository.getSecurityState()

            assertFalse(result.isOnHomeNetwork)
            assertTrue(result.hasHomeSetup)
        }

        @Test
        fun `Given not on home network and Ethernet enabled then hasHomeSetup is true`() = runTest {
            val repository = createServerConnectionStateProvider(
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

            val result = repository.getSecurityState()

            assertFalse(result.isOnHomeNetwork)
            assertTrue(result.hasHomeSetup)
        }

        @Test
        fun `Given location permission denied then locationEnabled is false`() = runTest {
            val repository = createServerConnectionStateProvider(
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

            val result = repository.getSecurityState()

            assertFalse(result.isOnHomeNetwork)
            assertFalse(result.locationEnabled)
        }

        @Test
        fun `Given location services disabled then locationEnabled is false`() = runTest {
            val repository = createServerConnectionStateProvider(
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

            val result = repository.getSecurityState()

            assertFalse(result.isOnHomeNetwork)
            assertFalse(result.locationEnabled)
        }

        @Test
        fun `Given both location permission denied and services disabled then locationEnabled is false`() = runTest {
            val repository = createServerConnectionStateProvider(
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

            val result = repository.getSecurityState()

            assertFalse(result.isOnHomeNetwork)
            assertFalse(result.locationEnabled)
        }

        @Test
        fun `Given no internalSsids and null internalVpn and internalEthernet then hasHomeSetup is false`() = runTest {
            val repository = createServerConnectionStateProvider(
                externalUrl = "http://external.example.com",
                internalUrl = null,
                internalVpn = null,
                internalEthernet = null,
            )
            every {
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            } returns PackageManager.PERMISSION_GRANTED
            every { DisabledLocationHandler.isLocationEnabled(context) } returns true

            val result = repository.getSecurityState()

            assertFalse(result.isOnHomeNetwork)
            assertFalse(result.hasHomeSetup)
        }
    }

    @Nested
    inner class GetExternalUrl {

        @Test
        fun `Given useCloud true and cloudUrl available when calling getExternalUrl then returns cloud URL`() = runTest {
            val provider = createServerConnectionStateProvider(
                externalUrl = "https://external.example.com",
                cloudUrl = "https://cloud.example.com",
                useCloud = true,
            )

            val result = provider.getExternalUrl()

            assertEquals("https://cloud.example.com/", result?.toString())
        }

        @ParameterizedTest(name = "getExternalUrl returns external URL: useCloud={0}, cloudUrl={1}")
        @CsvSource(
            "true, null",
            "false, https://cloud.example.com",
        )
        fun `Given cloud not available or disabled when calling getExternalUrl then returns external URL`(
            useCloud: Boolean,
            cloudUrl: String?,
        ) = runTest {
            val provider = createServerConnectionStateProvider(
                externalUrl = "https://external.example.com",
                cloudUrl = cloudUrl?.takeIf { it != "null" },
                useCloud = useCloud,
            )

            val result = provider.getExternalUrl()

            assertEquals("https://external.example.com/", result?.toString())
        }

        @Test
        fun `Given invalid external URL when calling getExternalUrl then returns null`() = runTest {
            val provider = createServerConnectionStateProvider(
                externalUrl = "not-a-valid-url",
                useCloud = false,
            )

            val result = provider.getExternalUrl()

            assertEquals(null, result)
        }
    }

    @Nested
    inner class GetApiUrls {

        @ParameterizedTest(name = "getApiUrls returns empty list when webhookId={0}")
        @NullAndEmptySource
        @ValueSource(strings = ["   "])
        fun `Given null or blank webhookId when calling getApiUrls then returns empty list`(webhookId: String?) = runTest {
            val provider = createServerConnectionStateProvider(
                externalUrl = "https://external.example.com",
                webhookId = webhookId,
            )

            val result = provider.getApiUrls()

            assertTrue(result.isEmpty())
        }

        @Test
        fun `Given on home network when calling getApiUrls then internal URL is first`() = runTest {
            val provider = createServerConnectionStateProvider(
                externalUrl = "https://external.example.com",
                internalUrl = "http://192.168.1.1:8123",
                webhookId = "webhook123",
                internalEthernet = true,
            )
            every { networkHelper.isUsingEthernet() } returns true

            val result = provider.getApiUrls()

            assertEquals(2, result.size)
            assertEquals("http://192.168.1.1:8123/api/webhook/webhook123", result[0].toString())
            assertEquals("https://external.example.com/api/webhook/webhook123", result[1].toString())
        }

        @Test
        fun `Given prioritizeInternal true when calling getApiUrls then internal URL is first`() = runTest {
            val provider = createServerConnectionStateProvider(
                externalUrl = "https://external.example.com",
                internalUrl = "http://192.168.1.1:8123",
                webhookId = "webhook123",
                prioritizeInternal = true,
            )

            val result = provider.getApiUrls()

            assertEquals(2, result.size)
            assertEquals("http://192.168.1.1:8123/api/webhook/webhook123", result[0].toString())
        }

        @Test
        fun `Given not on home network and has cloudhookUrl when calling getApiUrls then cloudhookUrl is first`() = runTest {
            val provider = createServerConnectionStateProvider(
                externalUrl = "https://external.example.com",
                internalUrl = "http://192.168.1.1:8123",
                cloudUrl = "https://abc123.ui.nabu.casa",
                cloudhookUrl = "https://hooks.nabu.casa/cloudhook123",
                webhookId = "webhook123",
            )

            val result = provider.getApiUrls()

            assertEquals(2, result.size)
            assertEquals("https://hooks.nabu.casa/cloudhook123", result[0].toString())
            assertEquals("https://external.example.com/api/webhook/webhook123", result[1].toString())
        }

        @Test
        fun `Given on home network with cloudhookUrl when calling getApiUrls then internal is first followed by cloudhookUrl`() = runTest {
            val provider = createServerConnectionStateProvider(
                externalUrl = "https://external.example.com",
                internalUrl = "http://192.168.1.1:8123",
                cloudUrl = "https://abc123.ui.nabu.casa",
                cloudhookUrl = "https://hooks.nabu.casa/cloudhook123",
                webhookId = "webhook123",
                internalEthernet = true,
            )
            every { networkHelper.isUsingEthernet() } returns true

            val result = provider.getApiUrls()

            assertEquals(3, result.size)
            assertEquals("http://192.168.1.1:8123/api/webhook/webhook123", result[0].toString())
            assertEquals("https://hooks.nabu.casa/cloudhook123", result[1].toString())
            assertEquals("https://external.example.com/api/webhook/webhook123", result[2].toString())
        }

        @Test
        fun `Given HTTP external URL and allowInsecure false and not on home network when calling getApiUrls then external URL is excluded`() = runTest {
            val provider = createServerConnectionStateProvider(
                externalUrl = "http://external.example.com",
                webhookId = "webhook123",
                allowInsecureConnection = false,
            )

            val result = provider.getApiUrls()

            assertTrue(result.isEmpty())
        }

        @Test
        fun `Given HTTP external URL and allowInsecure true and not on home network when calling getApiUrls then external URL is included`() = runTest {
            val provider = createServerConnectionStateProvider(
                externalUrl = "http://external.example.com",
                webhookId = "webhook123",
                allowInsecureConnection = true,
            )

            val result = provider.getApiUrls()

            assertEquals(1, result.size)
            assertEquals("http://external.example.com/api/webhook/webhook123", result[0].toString())
        }

        @Test
        fun `Given HTTP internal URL and prioritizeInternal true and allowInsecure false and not on home network when calling getApiUrls then internal URL is excluded`() = runTest {
            val provider = createServerConnectionStateProvider(
                externalUrl = "https://external.example.com",
                internalUrl = "http://192.168.1.1:8123",
                webhookId = "webhook123",
                prioritizeInternal = true,
                allowInsecureConnection = false,
            )

            val result = provider.getApiUrls()

            assertEquals(1, result.size)
            assertEquals("https://external.example.com/api/webhook/webhook123", result[0].toString())
        }

        @Test
        fun `Given HTTPS internal URL and prioritizeInternal true and allowInsecure false and not on home network when calling getApiUrls then internal URL is included`() = runTest {
            val provider = createServerConnectionStateProvider(
                externalUrl = "https://external.example.com",
                internalUrl = "https://192.168.1.1:8123",
                webhookId = "webhook123",
                prioritizeInternal = true,
                allowInsecureConnection = false,
            )

            val result = provider.getApiUrls()

            assertEquals(2, result.size)
            assertEquals("https://192.168.1.1:8123/api/webhook/webhook123", result[0].toString())
            assertEquals("https://external.example.com/api/webhook/webhook123", result[1].toString())
        }

        @Test
        fun `Given HTTPS external URL and allowInsecure false and not on home network when calling getApiUrls then external URL is included`() = runTest {
            val provider = createServerConnectionStateProvider(
                externalUrl = "https://external.example.com",
                webhookId = "webhook123",
                allowInsecureConnection = false,
            )

            val result = provider.getApiUrls()

            assertEquals(1, result.size)
            assertEquals("https://external.example.com/api/webhook/webhook123", result[0].toString())
        }
    }

    @Nested
    inner class CanSafelySendCredentials {

        @Test
        fun `Given HTTPS URL when calling canSafelySendCredentials then returns true`() = runTest {
            val provider = createServerConnectionStateProvider(
                externalUrl = "http://external.example.com",
            )

            assertTrue(provider.canSafelySendCredentials("https://any-url.com"))
        }

        @Test
        fun `Given HTTP URL not belonging to server when calling canSafelySendCredentials then returns false`() = runTest {
            val provider = createServerConnectionStateProvider(
                externalUrl = "https://external.example.com",
            )

            assertFalse(provider.canSafelySendCredentials("http://unknown.example.com"))
        }

        @Test
        fun `Given HTTP URL belonging to server and allowInsecure true when calling canSafelySendCredentials then returns true`() = runTest {
            val provider = createServerConnectionStateProvider(
                externalUrl = "http://external.example.com",
                allowInsecureConnection = true,
            )

            assertTrue(provider.canSafelySendCredentials("http://external.example.com/api"))
        }

        @Test
        fun `Given HTTP URL belonging to server and allowInsecure false but on home network when calling canSafelySendCredentials then returns true`() = runTest {
            val provider = createServerConnectionStateProvider(
                externalUrl = "http://external.example.com",
                internalUrl = "http://192.168.1.1:8123",
                allowInsecureConnection = false,
                internalEthernet = true,
            )
            every { networkHelper.isUsingEthernet() } returns true

            assertTrue(provider.canSafelySendCredentials("http://external.example.com/api"))
        }

        @Test
        fun `Given HTTP URL belonging to server and allowInsecure false and not internal when calling canSafelySendCredentials then returns false`() = runTest {
            val provider = createServerConnectionStateProvider(
                externalUrl = "http://external.example.com",
                allowInsecureConnection = false,
            )

            assertFalse(provider.canSafelySendCredentials("http://external.example.com/api"))
        }

        @Test
        fun `Given HTTP URL belonging to server and allowInsecure null when calling canSafelySendCredentials then returns true for backwards compatibility`() = runTest {
            val provider = createServerConnectionStateProvider(
                externalUrl = "http://external.example.com",
                allowInsecureConnection = null,
            )

            assertTrue(provider.canSafelySendCredentials("http://external.example.com/api"))
        }
    }

    @Nested
    inner class UrlFlow {

        @Test
        fun `Given HTTPS URL when collecting urlFlow then emits HasUrl`() = runTest {
            val provider = createServerConnectionStateProvider(
                externalUrl = "https://external.example.com",
            )
            every { serverDao.getFlow(serverId) } returns flowOf(null)

            val result = provider.urlFlow().first()

            assertTrue(result is UrlState.HasUrl)
            assertEquals("https://external.example.com/", (result as UrlState.HasUrl).url?.toString())
        }

        @Test
        fun `Given HTTP URL with allowInsecure true when collecting urlFlow then emits HasUrl`() = runTest {
            val provider = createServerConnectionStateProvider(
                externalUrl = "http://external.example.com",
                allowInsecureConnection = true,
            )
            every { serverDao.getFlow(serverId) } returns flowOf(null)

            val result = provider.urlFlow().first()

            assertTrue(result is UrlState.HasUrl)
            assertEquals("http://external.example.com/", (result as UrlState.HasUrl).url?.toString())
        }

        @Test
        fun `Given HTTP URL with allowInsecure false and not internal when collecting urlFlow then emits InsecureState`() = runTest {
            val provider = createServerConnectionStateProvider(
                externalUrl = "http://external.example.com",
                allowInsecureConnection = false,
            )
            every { serverDao.getFlow(serverId) } returns flowOf(null)

            val result = provider.urlFlow().first()

            assertTrue(result is UrlState.InsecureState)
        }

        @Test
        fun `Given HTTP URL with allowInsecure false but on home network when collecting urlFlow then emits HasUrl`() = runTest {
            val provider = createServerConnectionStateProvider(
                externalUrl = "http://external.example.com",
                internalUrl = "http://192.168.1.1:8123",
                allowInsecureConnection = false,
                internalEthernet = true,
            )
            every { networkHelper.isUsingEthernet() } returns true
            every { serverDao.getFlow(serverId) } returns flowOf(null)

            val result = provider.urlFlow().first()

            assertTrue(result is UrlState.HasUrl)
        }

        @Test
        fun `Given HTTP URL with allowInsecure null when collecting urlFlow then emits HasUrl for backwards compatibility`() = runTest {
            val provider = createServerConnectionStateProvider(
                externalUrl = "http://external.example.com",
                allowInsecureConnection = null,
            )
            every { serverDao.getFlow(serverId) } returns flowOf(null)

            val result = provider.urlFlow().first()

            assertTrue(result is UrlState.HasUrl)
        }

        @Test
        fun `Given isInternalOverride returns true when collecting urlFlow then uses internal URL`() = runTest {
            val provider = createServerConnectionStateProvider(
                externalUrl = "https://external.example.com",
                internalUrl = "http://192.168.1.1:8123",
                allowInsecureConnection = true,
            )
            every { serverDao.getFlow(serverId) } returns flowOf(null)

            val result = provider.urlFlow(isInternalOverride = { true }).first()

            assertTrue(result is UrlState.HasUrl)
            assertEquals("http://192.168.1.1:8123/", (result as UrlState.HasUrl).url?.toString())
        }

        @Test
        fun `Given on home network when collecting urlFlow then uses internal URL`() = runTest {
            val provider = createServerConnectionStateProvider(
                externalUrl = "https://external.example.com",
                internalUrl = "http://192.168.1.1:8123",
                internalEthernet = true,
                allowInsecureConnection = true,
            )
            every { networkHelper.isUsingEthernet() } returns true
            every { serverDao.getFlow(serverId) } returns flowOf(null)

            val result = provider.urlFlow().first()

            assertTrue(result is UrlState.HasUrl)
            assertEquals("http://192.168.1.1:8123/", (result as UrlState.HasUrl).url?.toString())
        }

        @Test
        fun `Given useCloud true and cloudUrl available when collecting urlFlow then uses cloud URL`() = runTest {
            val provider = createServerConnectionStateProvider(
                externalUrl = "https://external.example.com",
                cloudUrl = "https://cloud.example.com",
                useCloud = true,
            )
            every { serverDao.getFlow(serverId) } returns flowOf(null)

            val result = provider.urlFlow().first()

            assertTrue(result is UrlState.HasUrl)
            assertEquals("https://cloud.example.com/", (result as UrlState.HasUrl).url?.toString())
        }
    }
}
