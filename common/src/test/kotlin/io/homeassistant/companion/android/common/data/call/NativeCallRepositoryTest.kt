package io.homeassistant.companion.android.common.data.call

import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.servers.ServerConnectionStateProvider
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.UrlState
import io.homeassistant.companion.android.common.util.di.SuspendProvider
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import java.net.URI
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NativeCallRepositoryTest {
    @Test
    fun `Given response headers when cancelled before body dispatch then release the response`() = runTest {
        val manager = mockk<ServerManager>()
        val connection = mockk<ServerConnectionStateProvider>()
        val authentication = mockk<AuthenticationRepository>()
        coEvery { manager.getServer(1) } returns mockk()
        coEvery { manager.connectionStateProvider(1) } returns connection
        coEvery { manager.authenticationRepository(1) } returns authentication
        every { connection.urlFlow(any()) } returns flowOf(UrlState.HasUrl(URI("https://example.test").toURL()))
        coEvery { connection.canSafelySendCredentials(any()) } returns true
        coEvery { authentication.buildBearerToken() } returns "Bearer test-credential"
        val call = mockk<Call>(relaxed = true)
        val callback = slot<Callback>()
        val client = mockk<OkHttpClient>()
        val builder = mockk<OkHttpClient.Builder>()
        every { client.newBuilder() } returns builder
        every { builder.followRedirects(any()) } returns builder
        every { builder.followSslRedirects(any()) } returns builder
        every { builder.callTimeout(any<Long>(), any()) } returns builder
        every { builder.build() } returns client
        every { client.newCall(any()) } returns call
        every { call.enqueue(capture(callback)) } just Runs
        var pending: Runnable? = null
        val dispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                pending = block
            }
        }
        var closes = 0
        val source = object : ForwardingSource(Buffer().writeUtf8("{}")) {
            override fun close() {
                closes++
                super.close()
            }
        }.buffer()
        val body = object : ResponseBody() {
            override fun contentType(): MediaType? = null
            override fun contentLength(): Long = 2
            override fun source(): BufferedSource = source
        }
        val response = Response.Builder().request(Request.Builder().url("https://example.test/api/call").build())
            .protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body).build()
        val repository = NativeCallRepository(manager, SuspendProvider { client }, dispatcher)
        val job = launch { repository.request(NativeCallInvitation(1, "call", "/api/call")) }
        runCurrent()
        callback.captured.onResponse(call, response)
        runCurrent()
        assertNotNull(pending)
        job.cancel()
        pending!!.run()
        runCurrent()
        job.join()
        assertEquals(1, closes)
    }
}
