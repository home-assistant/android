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
    fun `hasSameBase returns expected value`() {
        assertHasSameBase("https://example.com", "https://example.com", true)
        assertHasSameBase("https://example.com/path", "https://example.com", true)
        assertHasSameBase("https://example.com?query=1", "https://example.com", true)
        assertHasSameBase("https://example.com:8123", "https://example.com:8123", true)
        assertHasSameBase("https://EXAMPLE.com", "https://example.com", true)
        assertHasSameBase("HTTPS://example.com", "https://example.com", true)
        assertHasSameBase("https://example.com", "https://other.com", false)
        assertHasSameBase("https://example.com", "http://example.com", false)
        assertHasSameBase("https://example.com:8123", "https://example.com:8124", false)
        assertHasSameBase("https://sub.example.com", "https://example.com", false)
    }

    private fun assertHasSameBase(url1: String, url2: String, expected: Boolean) {
        val uri1 = Uri.parse(url1)
        val uri2 = Uri.parse(url2)
        assertEquals("hasSameBase($url1, $url2)", expected, uri1.hasSameBase(uri2))
    }

    @Test
    fun `hasSameBase returns false when other is null`() {
        val uri = Uri.parse("https://example.com")
        assertFalse(uri.hasSameBase(null))
    }

    @Test
    fun `hasMeaningfulPath returns expected value`() {
        assertHasMeaningfulPath("https://example.com/path", true)
        assertHasMeaningfulPath("https://example.com/lovelace/default", true)
        assertHasMeaningfulPath("https://example.com/a", true)
        assertHasMeaningfulPath("https://example.com/", false)
        assertHasMeaningfulPath("https://example.com", false)
    }

    private fun assertHasMeaningfulPath(url: String, expected: Boolean) {
        val uri = Uri.parse(url)
        assertEquals("hasMeaningfulPath($url)", expected, uri.hasMeaningfulPath())
    }
}
