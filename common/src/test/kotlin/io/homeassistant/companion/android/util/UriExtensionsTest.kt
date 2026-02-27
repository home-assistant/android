package io.homeassistant.companion.android.util

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [Uri] extension functions defined in UrlUtil.kt.
 *
 * This test class uses Robolectric (JUnit 4) because [Uri] is an Android framework class
 * that requires the Android runtime to function properly. The main [UrlUtilTest] uses
 * JUnit 5 for tests that don't require Android classes.
 */
@RunWith(RobolectricTestRunner::class)
class UriExtensionsTest {

    @Test
    fun `hasSameOrigin returns expected value`() {
        assertHasSameOrigin("https://example.com", "https://example.com", true)
        assertHasSameOrigin("https://example.com/path", "https://example.com", true)
        assertHasSameOrigin("https://example.com?query=1", "https://example.com", true)
        assertHasSameOrigin("https://example.com:443", "https://example.com", true)
        assertHasSameOrigin("http://example.com:80", "http://example.com", true)
        assertHasSameOrigin("https://example.com:8123", "https://example.com:8123", true)
        assertHasSameOrigin("https://example.com", "https://other.com", false)
        assertHasSameOrigin("https://example.com", "http://example.com", false)
        assertHasSameOrigin("https://example.com:8123", "https://example.com:8124", false)
        assertHasSameOrigin("https://example.com", "https://example.com.evil.com", false)
        assertHasSameOrigin("https://sub.example.com", "https://example.com", false)
    }

    private fun assertHasSameOrigin(url1: String, url2: String, expected: Boolean) {
        val uri1 = Uri.parse(url1)
        val uri2 = Uri.parse(url2)
        assertEquals("hasSameOrigin($url1, $url2)", expected, uri1.hasSameOrigin(uri2))
    }

    @Test
    fun `hasSameOrigin returns false when other is null`() {
        val uri = Uri.parse("https://example.com")
        assertFalse(uri.hasSameOrigin(null))
    }

    @Test
    fun `hasNonRootPath returns expected value`() {
        assertHasNonRootPath("https://example.com/path", true)
        assertHasNonRootPath("https://example.com/lovelace/default", true)
        assertHasNonRootPath("https://example.com/a", true)
        assertHasNonRootPath("https://example.com/", false)
        assertHasNonRootPath("https://example.com", false)
    }

    private fun assertHasNonRootPath(url: String, expected: Boolean) {
        val uri = Uri.parse(url)
        assertEquals("hasNonRootPath($url)", expected, uri.hasNonRootPath())
    }

    // ---- toRelativeUrl tests ----

    @Test
    fun `toRelativeUrl returns path for URL with path only`() {
        val uri = Uri.parse("https://example.com/lovelace/default")
        assertEquals("/lovelace/default", uri.toRelativeUrl())
    }

    @Test
    fun `toRelativeUrl returns path and query params`() {
        val uri = Uri.parse("https://example.com/history?start_date=2026-01-01&end_date=2026-01-31")
        assertEquals("/history?start_date=2026-01-01&end_date=2026-01-31", uri.toRelativeUrl())
    }

    @Test
    fun `toRelativeUrl returns path query and fragment`() {
        val uri = Uri.parse("https://example.com/history?start_date=2026-01-01#tab")
        assertEquals("/history?start_date=2026-01-01#tab", uri.toRelativeUrl())
    }

    @Test
    fun `toRelativeUrl excludes specified query params`() {
        val uri = Uri.parse("https://example.com/dashboard?external_auth=1&lang=en")
        assertEquals("/dashboard?lang=en", uri.toRelativeUrl(excludeParams = setOf("external_auth")))
    }

    @Test
    fun `toRelativeUrl returns null when only excluded params remain`() {
        val uri = Uri.parse("https://example.com/dashboard?external_auth=1")
        assertEquals("/dashboard", uri.toRelativeUrl(excludeParams = setOf("external_auth")))
    }

    @Test
    fun `toRelativeUrl returns null for root path`() {
        val uri = Uri.parse("https://example.com/")
        assertNull(uri.toRelativeUrl())
    }

    @Test
    fun `toRelativeUrl returns null for URL without path`() {
        val uri = Uri.parse("https://example.com")
        assertNull(uri.toRelativeUrl())
    }

    @Test
    fun `toRelativeUrl preserves fragment when no query params`() {
        val uri = Uri.parse("https://example.com/settings#advanced")
        assertEquals("/settings#advanced", uri.toRelativeUrl())
    }

    @Test
    fun `toRelativeUrl excludes multiple params`() {
        val uri = Uri.parse("https://example.com/view?external_auth=1&token=abc&lang=en")
        assertEquals(
            "/view?lang=en",
            uri.toRelativeUrl(excludeParams = setOf("external_auth", "token")),
        )
    }

    @Test
    fun `toRelativeUrl preserves all params when no exclusions`() {
        val uri = Uri.parse("https://example.com/view?external_auth=1&lang=en")
        assertEquals("/view?external_auth=1&lang=en", uri.toRelativeUrl())
    }

    @Test
    fun `toRelativeUrl handles deeply nested paths`() {
        val uri = Uri.parse("https://example.com/config/devices/device/abc123")
        assertEquals("/config/devices/device/abc123", uri.toRelativeUrl())
    }
}
