package io.homeassistant.companion.android.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class UrlBuilderTest {

    private val urlBuilder = UrlBuilder()

    @Test
    fun `Given base URL and path segments when building URL then it combines them correctly`() {
        val result = urlBuilder.buildUrl(
            base = "http://example.com",
            pathSegments = "api/v1/users",
        )

        assertEquals("http://example.com/api/v1/users", result)
    }

    @Test
    fun `Given base URL with trailing slash and path segments when building URL then it combines them correctly`() {
        val result = urlBuilder.buildUrl(
            base = "http://example.com/",
            pathSegments = "api/v1/users",
        )

        assertEquals("http://example.com/api/v1/users", result)
    }

    @Test
    fun `Given base URL and empty path segments when building URL then it returns base URL`() {
        val result = urlBuilder.buildUrl(
            base = "http://example.com",
            pathSegments = "",
        )

        assertEquals("http://example.com/", result)
    }

    @Test
    fun `Given base URL path segments and query parameters when building URL then it combines all correctly`() {
        val result = urlBuilder.buildUrl(
            base = "http://example.com",
            pathSegments = "api/search",
            parameters = mapOf("q" to "test", "limit" to "10"),
        )

        assertEquals("http://example.com/api/search?q=test&limit=10", result)
    }

    @Test
    fun `Given base URL and single query parameter when building URL then it adds parameter correctly`() {
        val result = urlBuilder.buildUrl(
            base = "http://example.com",
            pathSegments = "api/users",
            parameters = mapOf("id" to "123"),
        )

        assertEquals("http://example.com/api/users?id=123", result)
    }

    @Test
    fun `Given base URL with existing path and new path segments when building URL then it replaces existing path`() {
        val result = urlBuilder.buildUrl(
            base = "http://example.com/old/path",
            pathSegments = "new/path",
        )

        assertEquals("http://example.com/new/path", result)
    }

    @Test
    fun `Given HTTPS base URL when building URL then it preserves HTTPS scheme`() {
        val result = urlBuilder.buildUrl(
            base = "https://secure.example.com",
            pathSegments = "api/data",
        )

        assertEquals("https://secure.example.com/api/data", result)
    }

    @Test
    fun `Given base URL with custom port when building URL then it preserves port`() {
        val result = urlBuilder.buildUrl(
            base = "http://example.com:8123",
            pathSegments = "api/users",
        )

        assertEquals("http://example.com:8123/api/users", result)
    }

    @Test
    fun `Given base URL with standard HTTP port when building URL then it omits port from result`() {
        val result = urlBuilder.buildUrl(
            base = "http://example.com:80",
            pathSegments = "api/users",
        )

        assertEquals("http://example.com/api/users", result)
    }

    @Test
    fun `Given base URL with standard HTTPS port when building URL then it omits port from result`() {
        val result = urlBuilder.buildUrl(
            base = "https://example.com:443",
            pathSegments = "api/users",
        )

        assertEquals("https://example.com/api/users", result)
    }

    @Test
    fun `Given path segments with spaces when building URL then it encodes spaces correctly`() {
        val result = urlBuilder.buildUrl(
            base = "http://example.com",
            pathSegments = "api/user profile",
        )

        assertEquals("http://example.com/api/user%20profile", result)
    }

    @Test
    fun `Given query parameter with special characters when building URL then it encodes them correctly`() {
        val result = urlBuilder.buildUrl(
            base = "http://example.com",
            pathSegments = "search",
            parameters = mapOf("q" to "hello world"),
        )

        assertEquals("http://example.com/search?q=hello%20world", result)
    }

    @Test
    fun `Given query parameter with encoded value when building URL then it preserves encoding`() {
        val result = urlBuilder.buildUrl(
            base = "http://example.com",
            pathSegments = "search",
            parameters = mapOf("q" to "hello%20world"),
        )

        assertEquals("http://example.com/search?q=hello%20world", result)
    }

    @Test
    fun `Given multiple path segments with slashes when building URL then it constructs path correctly`() {
        val result = urlBuilder.buildUrl(
            base = "http://example.com",
            pathSegments = "api/v2/users/profile",
        )

        assertEquals("http://example.com/api/v2/users/profile", result)
    }

    @Test
    fun `Given empty parameters map when building URL then it builds URL without query string`() {
        val result = urlBuilder.buildUrl(
            base = "http://example.com",
            pathSegments = "api/users",
            parameters = emptyMap(),
        )

        assertEquals("http://example.com/api/users", result)
    }

    @Test
    fun `Given base URL with query parameters and additional parameters when building URL then it replaces query parameters`() {
        val result = urlBuilder.buildUrl(
            base = "http://example.com/path?old=value",
            pathSegments = "new/path",
            parameters = mapOf("new" to "parameter"),
        )

        assertEquals("http://example.com/new/path?new=parameter", result)
    }

    @ParameterizedTest
    @CsvSource(
        "localhost, localhost",
        "192.168.1.1, 192.168.1.1",
        "example.com, example.com",
        "sub.example.com, sub.example.com",
    )
    fun `Given different host types when building URL then it preserves host correctly`(
        host: String,
        expectedHost: String,
    ) {
        val result = urlBuilder.buildUrl(
            base = "http://$host",
            pathSegments = "api",
        )

        assertEquals("http://$expectedHost/api", result)
    }

    @Test
    fun `Given base URL with subdomain when building URL then it preserves subdomain`() {
        val result = urlBuilder.buildUrl(
            base = "https://api.example.com",
            pathSegments = "v1/users",
        )

        assertEquals("https://api.example.com/v1/users", result)
    }

    @Test
    fun `Given path segments with leading slash when building URL then it handles it correctly`() {
        val result = urlBuilder.buildUrl(
            base = "http://example.com",
            pathSegments = "/api/users",
        )

        assertEquals("http://example.com//api/users", result)
    }

    @Test
    fun `Given query parameters with empty values when building URL then it includes parameters with empty values`() {
        val result = urlBuilder.buildUrl(
            base = "http://example.com",
            pathSegments = "api",
            parameters = mapOf("key" to ""),
        )

        assertEquals("http://example.com/api?key=", result)
    }

    @Test
    fun `Given multiple query parameters when building URL then it adds all parameters`() {
        val result = urlBuilder.buildUrl(
            base = "http://example.com",
            pathSegments = "search",
            parameters = mapOf(
                "query" to "test",
                "limit" to "50",
                "offset" to "100",
                "sort" to "asc",
            ),
        )

        assertEquals("http://example.com/search?query=test&limit=50&offset=100&sort=asc", result)
    }
}
