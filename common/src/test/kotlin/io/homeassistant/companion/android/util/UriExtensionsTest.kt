package io.homeassistant.companion.android.util

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
