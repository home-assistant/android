package io.homeassistant.companion.android.common.data.connectivity

import io.homeassistant.companion.android.common.data.network.NetworkAwareDns
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Test

class DefaultConnectivityCheckerCancellationTest {

    @Test
    fun `Given coroutine cancelled when executing TLS check then cancels OkHttp call`() = runTest {
        val call = mockk<Call>(relaxed = true)
        val callbackSlot = slot<Callback>()
        val okHttpClient = mockk<OkHttpClient>()
        every { okHttpClient.newCall(any()) } returns call
        every { call.enqueue(capture(callbackSlot)) } just runs

        val checker = DefaultConnectivityChecker(
            networkAwareDns = mockk<NetworkAwareDns>(relaxed = true),
            okHttpClient = okHttpClient,
        )

        val job = launch(Dispatchers.Unconfined) {
            checker.tls("https://example.com")
        }
        delay(1)
        job.cancel()

        verify(timeout = 1_000) { call.cancel() }
    }
}
