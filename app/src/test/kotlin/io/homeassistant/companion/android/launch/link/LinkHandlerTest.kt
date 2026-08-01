package io.homeassistant.companion.android.launch.link

import androidx.core.net.toUri
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.frontend.navigation.FrontendTarget
import io.homeassistant.companion.android.util.UrlUtil
import io.mockk.coEvery
import io.mockk.mockk
import java.net.URL
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertNotNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// We need Robolectric because of the usage of URI from `android.net.URI`
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class LinkHandlerTest {
    private val serverManager: ServerManager = mockk()
    private val handler = LinkHandlerImpl(serverManager)

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
    fun `Given unknown deep link host when invoking handleLink then returns NoDestination`() = runTest {
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
    fun `Given redirect URI with single registered server when invoking handleLink then returns Webview with provided path without trailing slash in path`() = runTest {
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.servers() } returns listOf(
            mockk {
                coEvery { friendlyName } returns "Home"
                coEvery { id } returns 1
            },
        )
        val uri = "https://my.home-assistant.io/redirect/supervisor_add_addon_repository/?repository_url=https%3A%2F%2Fgithub.com%2Fhome-assistant%2Fandroid%2F".toUri()

        val result = handler.handleLink(uri)
        assertEquals(LinkDestination.Webview(FrontendTarget.Path("_my_redirect/supervisor_add_addon_repository?repository_url=https%3A%2F%2Fgithub.com%2Fhome-assistant%2Fandroid%2F&mobile=1"), ServerManager.SERVER_ID_ACTIVE), result)
    }

    @Test
    fun `Given redirect URI with no registered server when invoking handleLink then returns NoDestination`() = runTest {
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

        assertEquals(LinkDestination.Webview(FrontendTarget.Path("lovelace/dashboard"), 1), result)
    }

    @Test
    fun `Given navigate deep link with registered server and default server param when invoking handleLink then returns Webview with default server`() = runTest {
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.getServer() } returns mockk {
            coEvery { id } returns 1
        }

        val uri = "homeassistant://navigate/lovelace/dashboard?server=default".toUri()
        val result = handler.handleLink(uri)

        assertEquals(LinkDestination.Webview(FrontendTarget.Path("lovelace/dashboard"), 1), result)
    }

    @Test
    fun `Given navigate deep link with registered server and empty server param when invoking handleLink then returns Webview with default server`() = runTest {
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.getServer() } returns mockk {
            coEvery { id } returns 1
        }

        val uri = "homeassistant://navigate/lovelace/dashboard?server=".toUri()
        val result = handler.handleLink(uri)

        assertEquals(LinkDestination.Webview(FrontendTarget.Path("lovelace/dashboard"), 1), result)
    }

    @Test
    fun `Given navigate deep link with registered server and specific server name when invoking handleLink then returns Webview with matching server`() = runTest {
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.servers() } returns listOf(
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

        assertEquals(LinkDestination.Webview(FrontendTarget.Path("lovelace/dashboard"), 2), result)
    }

    @Test
    fun `Given navigate deep link with server_id param when invoking handleLink then returns Webview with that server`() = runTest {
        coEvery { serverManager.isRegistered() } returns true

        val uri = "homeassistant://navigate/lovelace/dashboard?server_id=2".toUri()
        val result = handler.handleLink(uri)

        assertEquals(LinkDestination.Webview(FrontendTarget.Path("lovelace/dashboard"), 2), result)
    }

    @Test
    fun `Given navigate deep link with root more-info-entity-id when invoking handleLink then returns the entity target`() = runTest {
        coEvery { serverManager.isRegistered() } returns true

        val uri = "homeassistant://navigate/?more-info-entity-id=light.kitchen&server_id=2".toUri()
        val result = handler.handleLink(uri)

        assertEquals(LinkDestination.Webview(FrontendTarget.EntityMoreInfo("light.kitchen"), 2), result)
    }

    @Test
    fun `Given a navigate deep link built for an entity when invoking handleLink then it round-trips to the entity target`() = runTest {
        coEvery { serverManager.isRegistered() } returns true

        val uri = navigateDeepLinkUri(FrontendTarget.EntityMoreInfo("light.kitchen"), serverId = 2)
        val result = handler.handleLink(uri)

        assertEquals(LinkDestination.Webview(FrontendTarget.EntityMoreInfo("light.kitchen"), 2), result)
    }

    @Test
    fun `Given a path with a query when building the navigate deep link then the query stays a query`() = runTest {
        val uri = navigateDeepLinkUri(FrontendTarget.Path("dashboard-smartphone/0?kiosk"), serverId = 2)

        assertEquals("homeassistant://navigate/dashboard-smartphone/0?kiosk&server_id=2", uri.toString())
    }

    @Test
    fun `Given a navigate deep link built for a path with a query when invoking handleLink then it round-trips to the same path`() = runTest {
        coEvery { serverManager.isRegistered() } returns true

        val uri = navigateDeepLinkUri(FrontendTarget.Path("dashboard-smartphone/0?kiosk"), serverId = 2)
        val result = handler.handleLink(uri)

        assertEquals(LinkDestination.Webview(FrontendTarget.Path("dashboard-smartphone/0?kiosk"), 2), result)
    }

    @Test
    fun `Given compliant and non-compliant raw paths when validating then only compliant ones pass`() {
        assertTrue(isValidFrontendRawPath("lovelace/dashboard-1?kiosk&edit=1#section"))
        assertTrue(isValidFrontendRawPath("dashboard/my%20room"))
        assertFalse(isValidFrontendRawPath("lovelace/my room"))
        assertFalse(isValidFrontendRawPath("lovelace/café"))
    }

    @Test
    fun `Given a hand-typed path with spaces when building the navigate deep link then illegal characters are percent-encoded`() = runTest {
        coEvery { serverManager.isRegistered() } returns true

        val uri = navigateDeepLinkUri(FrontendTarget.Path("lovelace/my room?tab=my tab#my view"), serverId = 2)

        assertEquals("homeassistant://navigate/lovelace/my%20room?tab=my%20tab&server_id=2#my%20view", uri.toString())
        assertEquals(
            LinkDestination.Webview(FrontendTarget.Path("lovelace/my%20room?tab=my%20tab#my%20view"), 2),
            handler.handleLink(uri),
        )
    }

    @Test
    fun `Given a path with existing escapes when building the navigate deep link then escapes are not double-encoded`() = runTest {
        val uri = navigateDeepLinkUri(FrontendTarget.Path("dashboard/my%20room?kiosk"), serverId = 2)

        assertEquals("homeassistant://navigate/dashboard/my%20room?kiosk&server_id=2", uri.toString())
    }

    @Test
    fun `Given a path with a question mark in the fragment when building the navigate deep link then it round-trips without inventing a query`() = runTest {
        coEvery { serverManager.isRegistered() } returns true

        val uri = navigateDeepLinkUri(FrontendTarget.Path("lovelace/0#view?x=1"), serverId = 2)

        assertEquals("homeassistant://navigate/lovelace/0?server_id=2#view?x=1", uri.toString())
        assertEquals(LinkDestination.Webview(FrontendTarget.Path("lovelace/0#view?x=1"), 2), handler.handleLink(uri))
    }

    @Test
    fun `Given a navigate deep link built for an absolute URL path when invoking handleLink then it round-trips to the same URL`() = runTest {
        coEvery { serverManager.isRegistered() } returns true

        val uri = navigateDeepLinkUri(FrontendTarget.Path("http://192.168.1.5:8123/lovelace/0?kiosk"), serverId = 2)
        val result = handler.handleLink(uri)

        assertEquals(LinkDestination.Webview(FrontendTarget.Path("http://192.168.1.5:8123/lovelace/0?kiosk"), 2), result)
    }

    @Test
    fun `Given navigate deep link with a percent-encoded query in the path when invoking handleLink then it is kept encoded`() = runTest {
        coEvery { serverManager.isRegistered() } returns true

        val uri = "homeassistant://navigate/dashboard-smartphone/0%3Fkiosk?server_id=2".toUri()
        val result = handler.handleLink(uri)

        assertEquals(LinkDestination.Webview(FrontendTarget.Path("dashboard-smartphone/0%3Fkiosk"), 2), result)
    }

    @Test
    fun `Given navigate deep link with a percent-encoded space in the path when invoking handleLink then the encoding is preserved and resolves`() = runTest {
        coEvery { serverManager.isRegistered() } returns true

        val uri = "homeassistant://navigate/lovelace/my%20dashboard?server_id=2".toUri()
        val result = handler.handleLink(uri)

        assertEquals(LinkDestination.Webview(FrontendTarget.Path("lovelace/my%20dashboard"), 2), result)

        val base = URL("http://homeassistant.local:8123/")
        assertEquals(
            "http://homeassistant.local:8123/lovelace/my%20dashboard",
            UrlUtil.handle(base, "lovelace/my%20dashboard").toString(),
        )
    }

    @Test
    fun `Given navigate deep link with query and fragment when invoking handleLink then both are kept without server params`() = runTest {
        coEvery { serverManager.isRegistered() } returns true

        val uri = "homeassistant://navigate/lovelace/dashboard?kiosk&server_id=2&edit=1#section".toUri()
        val result = handler.handleLink(uri)

        assertEquals(LinkDestination.Webview(FrontendTarget.Path("lovelace/dashboard?kiosk&edit=1#section"), 2), result)
    }

    @Test
    fun `Given navigate deep link with registered server and case-insensitive server name when invoking handleLink then returns Webview with matching server`() = runTest {
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.servers() } returns listOf(
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

        assertEquals(LinkDestination.Webview(FrontendTarget.Path("lovelace/dashboard"), 2), result)
    }

    @Test
    fun `Given navigate deep link with registered server and non-existing server name when invoking handleLink then returns Webview with SERVER_ID_ACTIVE serverId`() = runTest {
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.servers() } returns listOf(
            mockk {
                coEvery { friendlyName } returns "Home"
                coEvery { id } returns 1
            },
        )

        val uri = "homeassistant://navigate/lovelace/dashboard?server=NonExisting".toUri()
        val result = handler.handleLink(uri)

        assertEquals(LinkDestination.Webview(FrontendTarget.Path("lovelace/dashboard"), ServerManager.SERVER_ID_ACTIVE), result)
    }

    /*
        ServerPicker section
     */
    @Test
    fun `Given redirect URI with multiple registered servers when invoking handleLink then returns ServerPicker`() = runTest {
        coEvery { serverManager.isRegistered() } returns true
        val servers = listOf<Server>(
            mockk {
                coEvery { friendlyName } returns "Home"
                coEvery { id } returns 1
            },
            mockk {
                coEvery { friendlyName } returns "Office"
                coEvery { id } returns 2
            },
        )
        coEvery { serverManager.servers() } returns servers
        val uri = "https://my.home-assistant.io/redirect/supervisor_add_addon_repository/?repository_url=https%3A%2F%2Fgithub.com%2Fhome-assistant%2Fandroid%2F".toUri()

        val result = handler.handleLink(uri)

        assertEquals(
            LinkDestination.ServerPicker(
                FrontendTarget.Path(
                    "_my_redirect/supervisor_add_addon_repository?repository_url=https%3A%2F%2Fgithub.com%2Fhome-assistant%2Fandroid%2F&mobile=1",
                ),
                servers,
            ),
            result,
        )
    }

    @Test
    fun `Given navigate deep link with non-existing server name and multiple servers when invoking handleLink then returns ServerPicker`() = runTest {
        coEvery { serverManager.isRegistered() } returns true
        val servers = listOf<Server>(
            mockk {
                coEvery { friendlyName } returns "Home"
                coEvery { id } returns 1
            },
            mockk {
                coEvery { friendlyName } returns "Office"
                coEvery { id } returns 2
            },
        )
        coEvery { serverManager.servers() } returns servers

        val uri = "homeassistant://navigate/lovelace/dashboard?server=NonExisting".toUri()
        val result = handler.handleLink(uri)

        assertEquals(
            LinkDestination.ServerPicker(FrontendTarget.Path("lovelace/dashboard"), servers),
            result,
        )
    }

    @Test
    fun `Given navigate deep link with no default server and multiple servers when invoking handleLink then returns ServerPicker`() = runTest {
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.getServer() } returns null
        val servers = listOf<Server>(
            mockk {
                coEvery { friendlyName } returns "Home"
                coEvery { id } returns 1
            },
            mockk {
                coEvery { friendlyName } returns "Office"
                coEvery { id } returns 2
            },
        )
        coEvery { serverManager.servers() } returns servers

        val uri = "homeassistant://navigate/lovelace/dashboard".toUri()
        val result = handler.handleLink(uri)

        assertEquals(LinkDestination.ServerPicker(FrontendTarget.Path("lovelace/dashboard"), servers), result)
    }
}
