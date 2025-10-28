package io.homeassistant.companion.android.util

import java.net.URL
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class UrlUtilTest {

    @ParameterizedTest
    @ValueSource(
        strings = [
            "http://homeassistant.local:8123",
            "http://homeassistant.LOCAL:8123",
            "http://homeassistant.LoCaL:42",
            "https://homeassistant.local",
            "http://homeassistant.lan:8123",
            "http://homeassistant.home:8123",
            "http://homeassistant.internal:8123",
            "http://homeassistant.localdomain:8123",
            "http://homeassistant.local:8123/lovelace/default",
            "http://my.homeassistant.local:8123",
            "http://localhost:8123",
            "http://127.0.0.1:8123",
            "http://192.168.1.10:8123",
            "http://10.0.0.10:8123",
            "http://172.16.0.1:8123",
            "http://[::1]:8123",
            "http://169.254.1.1:8123", // link local IP
        ],
    )
    fun `Given local URL when checking isPubliclyAccessible then returns false`(url: String) = runTest {
        val url = URL(url)
        assertFalse(url.isPubliclyAccessible())
    }

    @Test
    fun `Given unresolved TLD when checking isPubliclyAccessible then returns false`() = runTest {
        val url = URL("http://this-domain-definitely-does-not-exist-12345.com:8123")
        assertFalse(url.isPubliclyAccessible())
    }

    @Test
    fun `Given URL with public domain resolving to public IP when checking isPubliclyAccessible then returns true`() = runTest {
        // Using a well-known public domain that should resolve to public IPs
        val url = URL("https://www.home-assistant.io")
        assertTrue(url.isPubliclyAccessible())
    }

    @Test
    fun `Given URL with Google DNS when checking isPubliclyAccessible then returns true`() = runTest {
        // Google's public DNS IP
        val url = URL("http://8.8.8.8:80")
        assertTrue(url.isPubliclyAccessible())
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "http://example.com",
            "http://homeassistant.local:8123",
            "http://192.168.1.10:8123",
            "http://localhost:8123/lovelace",
            "HTTP://EXAMPLE.COM",
            "HtTp://mixed.case.url:8080",
        ],
    )
    fun `Given HTTP URL when checking isHttp then returns true`(urlString: String) {
        val url = URL(urlString)
        assertTrue(url.isHttp())
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "https://example.com",
            "https://homeassistant.local:8123",
            "https://192.168.1.10:8123",
            "https://localhost:8123/lovelace",
            "HTTPS://EXAMPLE.COM",
            "HtTpS://mixed.case.url:8080",
        ],
    )
    fun `Given HTTPS URL when checking isHttp then returns false`(urlString: String) {
        val url = URL(urlString)
        assertFalse(url.isHttp())
    }
}
