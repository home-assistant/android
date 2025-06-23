package io.homeassistant.companion.android.launch.link

import androidx.core.net.toUri
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.FailFast
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertNotNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// We need Robolectric because of the usage of URI from `android.net.URI`
@RunWith(RobolectricTestRunner::class)
class LinkHandlerTest {

    private val serverManager: ServerManager = mockk()
    private val handler = LinkHandlerImpl(serverManager)

    /*
    General section
     */
    @Test
    fun `Given unknown URI when invoking handleMyLink then return NoDestination`() {
        var caughtException: Exception? = null
        FailFast.setHandler { exception, additionalMessage ->
            caughtException = exception
        }

        val uri = "https://my.home-assistant.io/unknown".toUri()
        val result = handler.handleLink(uri)
        assertEquals(LinkDestination.NoDestination, result)
        assertNotNull(caughtException)
    }

    @Test
    fun `Given unknown URI scheme when invoking handleMyLink then return NoDestination`() {
        var caughtException: Exception? = null
        FailFast.setHandler { exception, additionalMessage ->
            caughtException = exception
        }

        val uri = "http://my.home-assistant.io/invite#url=http://homeassistant.local:8123".toUri()
        val result = handler.handleLink(uri)
        assertEquals(LinkDestination.NoDestination, result)
        assertNotNull(caughtException)
    }

    @Test
    fun `Given unknown URI host when invoking handleMyLink then return NoDestination`() {
        var caughtException: Exception? = null
        FailFast.setHandler { exception, additionalMessage ->
            caughtException = exception
        }

        val uri = "https://my.home-assistant.ioa/invite#url=http://homeassistant.local:8123".toUri()
        val result = handler.handleLink(uri)
        assertEquals(LinkDestination.NoDestination, result)
        assertNotNull(caughtException)
    }

    /*
    invite section
     */
    @Test
    fun `Given valid invite URI with URL when invoking handleMyLink then return Onboarding with provided URL`() {
        val uri = "https://my.home-assistant.io/invite/#url=http://homeassistant.local:8123".toUri()
        val result = handler.handleLink(uri)
        assertEquals(LinkDestination.Onboarding("http://homeassistant.local:8123"), result)
    }

    @Test
    fun `Given valid invite deep link with URL when invoking handleMyLink then return Onboarding with provided URL`() {
        val uri = "homeassistant://invite/toto#url=http://homeassistant.local:8123".toUri()
        val result = handler.handleLink(uri)
        assertEquals(LinkDestination.Onboarding("http://homeassistant.local:8123"), result)
    }

    @Test
    fun `Given valid invite URI with empty URL when invoking handleMyLink then return Onboarding with empty URL`() {
        val uri = "https://my.home-assistant.io/invite/#url=".toUri()
        val result = handler.handleLink(uri)
        assertEquals(LinkDestination.Onboarding(""), result)
    }

    @Test
    fun `Given valid invite URI with multiples args when invoking handleMyLink then return Onboarding with only the url`() {
        val uri = "https://my.home-assistant.io/invite/#app=1&url=http://homeassistant.local:8123&repository_url=https%3A%2F%2Fgithub.com%2Fhome-assistant%2Fandroid%2F".toUri()
        val result = handler.handleLink(uri)
        assertEquals(LinkDestination.Onboarding("http://homeassistant.local:8123"), result)
    }

    @Test
    fun `Given valid invite URI with URL that contains params when invoking handleMyLink then return Onboarding with provided URL with params`() {
        // To support this case the URL in `url=` is encoded twice, when building the URL the first time and then a second time to be used as a parameter
        // of another URL.
        val uri = "https://my.home-assistant.io/invite/#url=http://homeassistant.local:8123?pre-auth=https%253A%252F%252Fgithub.com%252Fhome-assistant%252Fandroid%252F%2526toto=tata&second_param_out_of_url=1".toUri()
        val result = handler.handleLink(uri)
        assertEquals(LinkDestination.Onboarding("http://homeassistant.local:8123?pre-auth=https%3A%2F%2Fgithub.com%2Fhome-assistant%2Fandroid%2F%26toto=tata"), result)
    }

    @Test
    fun `Given no url in invite when invoking handleMyLink then return NoDestination`() {
        val uri = "https://my.home-assistant.io/invite/#".toUri()
        val result = handler.handleLink(uri)
        assertEquals(LinkDestination.NoDestination, result)
    }

    /*
    redirect section
     */
    @Test
    fun `Given redict URI with registered server when invoking handleMyLink then return Webview with provided path without trailing slash in path`() {
        every { serverManager.isRegistered() } returns true
        val uri = "https://my.home-assistant.io/redirect/supervisor_add_addon_repository/?repository_url=https%3A%2F%2Fgithub.com%2Fhome-assistant%2Fandroid%2F".toUri()

        val result = handler.handleLink(uri)
        assertEquals(LinkDestination.Webview("_my_redirect/supervisor_add_addon_repository?repository_url=https%3A%2F%2Fgithub.com%2Fhome-assistant%2Fandroid%2F&mobile=1"), result)
    }

    @Test
    fun `Given redict URI with no registered server when invoking handleMyLink then return NoDestination`() {
        every { serverManager.isRegistered() } returns false
        val uri = "https://my.home-assistant.io/redirect/supervisor_add_addon_repository/?repository_url=https%3A%2F%2Fgithub.com%2Fhome-assistant%2Fandroid%2F".toUri()

        val result = handler.handleLink(uri)
        assertEquals(LinkDestination.NoDestination, result)
    }

    @Test
    fun `Given server registered and a valid URI with mobile flag set when invoking handleMyLink then return NoDestination`() {
        every { serverManager.isRegistered() } returns true
        val uri = "https://my.home-assistant.io/redirect/supervisor_add_addon_repository/?repository_url=https%3A%2F%2Fgithub.com%2Fhome-assistant%2Fandroid%2F&mobile=1".toUri()

        val result = handler.handleLink(uri)
        assertEquals(LinkDestination.NoDestination, result)
    }
}
