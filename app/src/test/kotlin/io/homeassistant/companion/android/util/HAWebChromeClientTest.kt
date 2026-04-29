package io.homeassistant.companion.android.util

import android.webkit.JsResult
import android.webkit.PermissionRequest
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ConsoleLogExtension::class)
class HAWebChromeClientTest {

    @Test
    fun `Given onPermissionRequest callback when permission requested then callback is invoked`() {
        var capturedRequest: PermissionRequest? = null
        val client = HAWebChromeClient(onPermissionRequest = { capturedRequest = it })
        val request = mockk<PermissionRequest>(relaxed = true)

        client.onPermissionRequest(request)

        assertTrue(capturedRequest === request)
    }

    @Test
    fun `Given onPermissionRequest with null request then callback is not invoked`() {
        var invoked = false
        val client = HAWebChromeClient(onPermissionRequest = { invoked = true })

        client.onPermissionRequest(null)

        assertFalse(invoked)
    }

    @Test
    fun `Given onJsConfirm callback when confirm triggered then callback receives message and result`() {
        var capturedMessage: String? = null
        var capturedResult: JsResult? = null
        val client = HAWebChromeClient(
            onJsConfirm = { message, result ->
                capturedMessage = message
                capturedResult = result
                true
            },
        )
        val jsResult = mockk<JsResult>(relaxed = true)

        val handled = client.onJsConfirm(mockk(relaxed = true), "https://example.com", "Proceed?", jsResult)

        assertTrue(handled)
        assertTrue(capturedMessage == "Proceed?")
        assertTrue(capturedResult === jsResult)
    }

    @Test
    fun `Given onJsConfirm with null message then callback is not invoked`() {
        var invoked = false
        val client = HAWebChromeClient(
            onJsConfirm = { _, _ ->
                invoked = true
                true
            },
        )
        val jsResult = mockk<JsResult>(relaxed = true)

        client.onJsConfirm(mockk(relaxed = true), "https://example.com", null, jsResult)

        assertFalse(invoked)
    }

    @Test
    fun `Given onJsConfirm with null result then callback is not invoked`() {
        var invoked = false
        val client = HAWebChromeClient(
            onJsConfirm = { _, _ ->
                invoked = true
                true
            },
        )

        client.onJsConfirm(mockk(relaxed = true), "https://example.com", "Proceed?", null)

        assertFalse(invoked)
    }

    @Test
    fun `Given no onJsConfirm callback when confirm triggered then returns false`() {
        val client = HAWebChromeClient()
        val jsResult = mockk<JsResult>(relaxed = true)

        val handled = client.onJsConfirm(mockk(relaxed = true), "https://example.com", "Proceed?", jsResult)

        assertFalse(handled)
        verify(exactly = 0) { jsResult.confirm() }
        verify(exactly = 0) { jsResult.cancel() }
    }
}
