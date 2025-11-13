package io.homeassistant.companion.android.util

import java.net.URL
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class UrlUtilTest {

    private lateinit var baseUrl: URL

    @BeforeEach
    fun setUp() {
        baseUrl = URL("https://example.com:8123/")
    }

    @ParameterizedTest
    @CsvSource(
        value = [
            "http://another.com/test,http://another.com/test",
            "https://secure.com/path,https://secure.com/path",
            "http://another.com:9000/path,http://another.com:9000/path",
        ],
    )
    fun `Given absolute URL when calling handle then returns parsed URL`(input: String, expected: String) {
        val result = UrlUtil.handle(baseUrl, input)

        assertNotNull(result)
        assertEquals(expected, result.toString())
    }

    @ParameterizedTest
    @CsvSource(
        value = [
            "lovelace/default,https://example.com:8123/lovelace/default",
            "/lovelace/default,https://example.com:8123/lovelace/default",
            "lovelace/default?edit=1,https://example.com:8123/lovelace/default?edit=1",
            "lovelace/default#section,https://example.com:8123/lovelace/default#section",
            "lovelace/default?edit=1#section,https://example.com:8123/lovelace/default?edit=1#section",
            "api/states/light.living_room,https://example.com:8123/api/states/light.living_room",
            "lovelace?key=value&other=test,https://example.com:8123/lovelace?key=value&other=test",
            "path/with%20encoded%20spaces,https://example.com:8123/path/with%20encoded%20spaces",
        ],
    )
    fun `Given relative path when calling handle then returns URL resolved against base`(input: String, expected: String) {
        val result = UrlUtil.handle(baseUrl, input)

        assertNotNull(result)
        assertEquals(expected, result.toString())
    }

    @Test
    fun `Given input with homeassistant navigate prefix and absolute URL when calling handle then treats as relative path without taking care of second host and protocol`() {
        val input = "homeassistant://navigate/https://example2.com/path/subpath"

        val result = UrlUtil.handle(baseUrl, input)

        assertNotNull(result)
        assertEquals("https://example.com:8123/path/subpath", result.toString())
    }

    @Test
    fun `Given input with homeassistant navigate prefix and relative path when calling handle then returns resolved URL`() {
        val input = "homeassistant://navigate/lovelace/default"

        val result = UrlUtil.handle(baseUrl, input)

        assertNotNull(result)
        assertEquals("https://example.com:8123/lovelace/default", result.toString())
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "",
            "   ",
        ],
    )
    fun `Given empty or whitespace input when calling handle then returns base URL`(input: String) {
        val result = UrlUtil.handle(baseUrl, input)

        assertNotNull(result)
        assertEquals("https://example.com:8123/", result.toString())
    }

    @Test
    fun `Given invalid URI input when calling handle then returns base URL`() {
        val input = "not a valid uri with spaces and bad chars <>"

        val result = UrlUtil.handle(baseUrl, input)

        assertEquals(baseUrl, result)
    }

    @Test
    fun `Given null base and relative path when calling handle then returns null`() {
        val input = "lovelace/default"

        val result = UrlUtil.handle(null, input)

        assertNull(result)
    }

    @Test
    fun `Given valid base url and invalid input URI that cannot be parsed into URL when calling handle then returns null`() {
        val input = "http://h:8123None"

        assertNull(UrlUtil.handle(baseUrl, input))
    }

    @Test
    fun `Given null base and absolute URL when calling handle then returns parsed URL`() {
        val input = "https://example.com/test"

        val result = UrlUtil.handle(null, input)

        assertNotNull(result)
        assertEquals("https://example.com/test", result.toString())
    }

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
}
