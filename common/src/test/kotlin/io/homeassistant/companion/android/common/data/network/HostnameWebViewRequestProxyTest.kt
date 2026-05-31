package io.homeassistant.companion.android.common.data.network

import android.net.Uri
import android.webkit.WebResourceRequest
import io.mockk.every
import io.mockk.mockk
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HostnameWebViewRequestProxyTest {

    private val proxy = HostnameWebViewRequestProxy(OkHttpClient())

    @Test
    fun `Given blank logical hostname when intercepting request then returns null`() {
        val request = mockRequest("https://home.example.com/")

        assertNull(proxy.intercept(null, request))
        assertNull(proxy.intercept(" ", request))
    }

    @Test
    fun `Given different hostname when intercepting request then returns null`() {
        val request = mockRequest("https://other.example.com/")

        assertNull(proxy.intercept("home.example.com", request))
    }

    @Test
    fun `Given POST request when intercepting then returns null`() {
        val request = mockRequest("https://home.example.com/api", method = "POST")

        assertNull(proxy.intercept("home.example.com", request))
    }

    private fun mockRequest(url: String, method: String = "GET"): WebResourceRequest {
        val host = url.removePrefix("https://").removePrefix("http://").substringBefore('/').substringBefore(':')
        val uri = mockk<Uri>(relaxed = true)
        every { uri.toString() } returns url
        every { uri.host } returns host
        return mockk {
            every { this@mockk.url } returns uri
            every { this@mockk.method } returns method
            every { requestHeaders } returns emptyMap()
        }
    }
}
