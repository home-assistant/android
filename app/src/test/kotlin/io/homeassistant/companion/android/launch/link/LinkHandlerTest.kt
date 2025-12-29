package io.homeassistant.companion.android.launch.link

import androidx.core.net.toUri
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.FailFast
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.fail
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// We need Robolectric because of the usage of URI from `android.net.URI`
@RunWith(RobolectricTestRunner::class)
class LinkHandlerTest {

    private val serverManager: ServerManager = mockk()
    private val handler = LinkHandlerImpl(serverManager)

    @Before
    fun setUp() {
        FailFast.setHandler { exception, additionalMessage ->
            fail("Unhandled exception caught", exception)
        }
    }

    /*
        General section
     */
    @Test
    fun `Given unknown URI when invoking handleLink then returns NoDestination`() = runTest {
        var caughtException: Throwable? = null
        FailFast.setHandler { exception, additionalMessage ->
            caughtException = exception
        }

        val uri = "https://my.home-assistant.io/unknown".toUri()
        val result = handler.handleLink(uri)
        assertEquals(LinkDestination.NoDestination, result)
        assertNotNull(caughtException)
    }

    @Test
    fun `Given unknown URI scheme when invoking handleLink then returns NoDestination`() = runTest {
        var caughtException: Throwable? = null
        FailFast.setHandler { exception, additionalMessage ->
            caughtException = exception
        }

        val uri = "http://my.home-assistant.io/invite#url=http://homeassistant.local:8123".toUri()
        val result = handler.handleLink(uri)
        assertEquals(LinkDestination.NoDestination, result)
        assertNotNull(caughtException)
    }

    @Test
    fun `Given unknown URI host when invoking handleLink then returns NoDestination`() = runTest {
        var caughtException: Throwable? = null
        FailFast.setHandler { exception, additionalMessage ->
            caughtException = exception
        }

        val uri = "https://my.home-assistant.ioa/invite#url=http://homeassistant.local:8123".toUri()
        val result = handler.handleLink(uri)
        assertEquals(LinkDestination.NoDestination, result)
        assertNotNull(caughtException)
    }

    @Test
    fun `Given unknown deep link host when invoking handleLink then returns NosDestination`() = runTest {
        var caughtException: Throwable? = null
        FailFast.setHandler { exception, additionalMessage ->
            caughtException = exception
        }

        val uri = "homeassistant://helloworld".toUri()
        val result = handler.handleLink(uri)
        assertEquals(LinkDestination.NoDestination, result)
        assertNotNull(caughtException)
    }

    /*
        invite section
     */
    @Test
    fun `Given valid invite URI with URL when invoking handleLink then returns Onboarding with provided URL`() = runTest {
        val uri = "https://my.home-assistant.io/invite/#url=http://homeassistant.local:8123".toUri()
        val result = handler.handleLink(uri)
        assertEquals(LinkDestination.Onboarding("http://homeassistant.local:8123"), result)
    }

    @Test
    fun `Given valid invite deep link with URL when invoking handleLink then returns Onboarding with provided URL`() = runTest {
        val uri = "homeassistant://invite/toto#url=http://homeassistant.local:8123".toUri()
        val result = handler.handleLink(uri)
        assertEquals(LinkDestination.Onboarding("http://homeassistant.local:8123"), result)
    }

    @Test
    fun `Given valid invite URI with empty URL when invoking handleLink then returns Onboarding with empty URL`() = runTest {
        val uri = "https://my.home-assistant.io/invite/#url=".toUri()
        val result = handler.handleLink(uri)
        assertEquals(LinkDestination.Onboarding(""), result)
    }

    @Test
    fun `Given valid invite URI with multiples args when invoking handleLink then returns Onboarding with only the url`() = runTest {
        val uri = "https://my.home-assistant.io/invite/#app=1&url=http://homeassistant.local:8123&repository_url=https%3A%2F%2Fgithub.com%2Fhome-assistant%2Fandroid%2F".toUri()
        val result = handler.handleLink(uri)
        assertEquals(LinkDestination.Onboarding("http://homeassistant.local:8123"), result)
    }

    @Test
    fun `Given valid invite URI with URL that contains params when invoking handleLink then returns Onboarding with provided URL with params`() = runTest {
        // To support this case the URL in `url=` is encoded twice, when building the URL the first time and then a second time to be used as a parameter
        // of another URL.
        val uri = "https://my.home-assistant.io/invite/#url=http://homeassistant.local:8123?pre-auth=https%253A%252F%252Fgithub.com%252Fhome-assistant%252Fandroid%252F%2526toto=tata&second_param_out_of_url=1".toUri()
        val result = handler.handleLink(uri)
        assertEquals(LinkDestination.Onboarding("http://homeassistant.local:8123?pre-auth=https%3A%2F%2Fgithub.com%2Fhome-assistant%2Fandroid%2F%26toto=tata"), result)
    }

    @Test
    fun `Given no url in invite when invoking handleLink then returns NoDestination`() = runTest {
        var caughtException: Throwable? = null
        FailFast.setHandler { exception, additionalMessage ->
            caughtException = exception
        }
        val uri = "https://my.home-assistant.io/invite/#".toUri()
        val result = handler.handleLink(uri)
        assertEquals(LinkDestination.NoDestination, result)
        assertNotNull(caughtException)
    }

    /*
    redirect section
     */
    @Test
    fun `Given redict URI with registered server when invoking handleLink then returns Webview with provided path without trailing slash in path`() = runTest {
        coEvery { serverManager.isRegistered() } returns true
        val uri = "https://my.home-assistant.io/redirect/supervisor_add_addon_repository/?repository_url=https%3A%2F%2Fgithub.com%2Fhome-assistant%2Fandroid%2F".toUri()

        val result = handler.handleLink(uri)
        assertEquals(LinkDestination.Webview("_my_redirect/supervisor_add_addon_repository?repository_url=https%3A%2F%2Fgithub.com%2Fhome-assistant%2Fandroid%2F&mobile=1"), result)
    }

    @Test
    fun `Given redict URI with no registered server when invoking handleLink then returns NoDestination`() = runTest {
        coEvery { serverManager.isRegistered() } returns false
        val uri = "https://my.home-assistant.io/redirect/supervisor_add_addon_repository/?repository_url=https%3A%2F%2Fgithub.com%2Fhome-assistant%2Fandroid%2F".toUri()

        val result = handler.handleLink(uri)
        assertEquals(LinkDestination.NoDestination, result)
    }

    @Test
    fun `Given server registered and a valid URI with mobile flag set when invoking handleLink then returns NoDestination`() = runTest {
        var caughtException: Throwable? = null
        FailFast.setHandler { exception, additionalMessage ->
            caughtException = exception
        }
        coEvery { serverManager.isRegistered() } returns true
        val uri = "https://my.home-assistant.io/redirect/supervisor_add_addon_repository/?repository_url=https%3A%2F%2Fgithub.com%2Fhome-assistant%2Fandroid%2F&mobile=1".toUri()

        val result = handler.handleLink(uri)
        assertEquals(LinkDestination.NoDestination, result)
        assertNotNull(caughtException)
    }

    @Test
    fun `Given navigate deep link with no registered server when invoking handleLink then returns NoDestination`() = runTest {
        coEvery { serverManager.isRegistered() } returns false

        val uri = "homeassistant://navigate/lovelace/dashboard".toUri()
        val result = handler.handleLink(uri)

        assertEquals(LinkDestination.NoDestination, result)
    }

    @Test
    fun `Given navigate deep link with registered server and no server param when invoking handleLink then returns Webview with default server`() = runTest {
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.getServer() } returns mockk {
            coEvery { id } returns 1
        }

        val uri = "homeassistant://navigate/lovelace/dashboard".toUri()
        val result = handler.handleLink(uri)

        assertEquals(LinkDestination.Webview("homeassistant://navigate/lovelace/dashboard", 1), result)
    }

    @Test
    fun `Given navigate deep link with registered server and default server param when invoking handleLink then returns Webview with default server`() = runTest {
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.getServer() } returns mockk {
            coEvery { id } returns 1
        }

        val uri = "homeassistant://navigate/lovelace/dashboard?server=default".toUri()
        val result = handler.handleLink(uri)

        assertEquals(LinkDestination.Webview("homeassistant://navigate/lovelace/dashboard?server=default", 1), result)
    }

    @Test
    fun `Given navigate deep link with registered server and empty server param when invoking handleLink then returns Webview with default server`() = runTest {
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.getServer() } returns mockk {
            coEvery { id } returns 1
        }

        val uri = "homeassistant://navigate/lovelace/dashboard?server=".toUri()
        val result = handler.handleLink(uri)

        assertEquals(LinkDestination.Webview("homeassistant://navigate/lovelace/dashboard?server=", 1), result)
    }

    @Test
    fun `Given navigate deep link with registered server and specific server name when invoking handleLink then returns Webview with matching server`() = runTest {
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.defaultServers } returns listOf(
            mockk {
                coEvery { friendlyName } returns "Home"
                coEvery { id } returns 1
            },
            mockk {
                coEvery { friendlyName } returns "Office"
                coEvery { id } returns 2
            },
        )

        val uri = "homeassistant://navigate/lovelace/dashboard?server=Office".toUri()
        val result = handler.handleLink(uri)

        assertEquals(LinkDestination.Webview("homeassistant://navigate/lovelace/dashboard?server=Office", 2), result)
    }

    @Test
    fun `Given navigate deep link with registered server and case-insensitive server name when invoking handleLink then returns Webview with matching server`() = runTest {
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.defaultServers } returns listOf(
            mockk {
                coEvery { friendlyName } returns "Home"
                coEvery { id } returns 1
            },
            mockk {
                coEvery { friendlyName } returns "Office"
                coEvery { id } returns 2
            },
        )

        val uri = "homeassistant://navigate/lovelace/dashboard?server=office".toUri()
        val result = handler.handleLink(uri)

        assertEquals(LinkDestination.Webview("homeassistant://navigate/lovelace/dashboard?server=office", 2), result)
    }

    @Test
    fun `Given navigate deep link with registered server and non-existing server name when invoking handleLink then returns Webview with null serverId`() = runTest {
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.defaultServers } returns listOf(
            mockk {
                coEvery { friendlyName } returns "Home"
                coEvery { id } returns 1
            },
        )

        val uri = "homeassistant://navigate/lovelace/dashboard?server=NonExisting".toUri()
        val result = handler.handleLink(uri)

        assertEquals(LinkDestination.Webview("homeassistant://navigate/lovelace/dashboard?server=NonExisting", null), result)
    }
}
