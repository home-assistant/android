package io.homeassistant.companion.android.common.data

import kotlin.time.Duration.Companion.seconds
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class RequestTimeoutCallFactoryTest {
    @Test
    fun `Given an existing factory when the setting changes then new calls use it and old calls keep their timeout`() {
        var timeout = 30.seconds
        val readTimeouts = mutableListOf<Int>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            readTimeouts += chain.readTimeoutMillis()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("".toResponseBody())
                .build()
        }.build()
        val originalReadTimeout = client.readTimeoutMillis
        val factory = RequestTimeoutCallFactory(client) { timeout }
        val request = Request.Builder().url("https://example.com/").build()

        val first = factory.newCall(request)
        timeout = 10.seconds
        val second = factory.newCall(request)

        assertEquals(30_000_000_000L, first.timeout().timeoutNanos())
        assertEquals(10_000_000_000L, second.timeout().timeoutNanos())
        assertEquals(originalReadTimeout, client.readTimeoutMillis)
        assertSame(request, second.request())
        first.execute().close()
        second.execute().close()
        assertEquals(listOf(30_000, 10_000), readTimeouts)
    }
}
