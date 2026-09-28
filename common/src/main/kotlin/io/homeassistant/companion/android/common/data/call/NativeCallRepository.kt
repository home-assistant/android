package io.homeassistant.companion.android.common.data.call

import io.homeassistant.companion.android.common.data.authentication.AuthorizationException
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.firstUrlOrNull
import io.homeassistant.companion.android.common.util.di.SuspendProvider
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

private const val MAX_CALL_DESCRIPTION_BYTES = 16_384L
private const val REQUEST_TIMEOUT_SECONDS = 5L
private const val SOCKET_PING_SECONDS = 15L

/** Authenticated access to a call provider on an already registered HA server. */
@Singleton
class NativeCallRepository @Inject constructor(
    private val serverManager: ServerManager,
    private val clientProvider: SuspendProvider<OkHttpClient>,
    @NativeCallIoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    val mediaClientId: String = UUID.randomUUID().toString()

    /** Fetch or control the exact call referenced by an invitation. */
    suspend fun request(
        invitation: NativeCallInvitation,
        action: String? = null,
        clientId: String = "",
    ): NativeCallDescription {
        val builder = authorizedRequest(invitation.serverId, invitation.path)
        if (action != null) {
            val body = buildJsonObject {
                put("action", action)
                put("client_id", clientId)
            }
            builder.post(body.toString().toRequestBody("application/json".toMediaType()))
        }
        val client = clientProvider().newBuilder().followRedirects(false).followSslRedirects(false)
            .callTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).build()
        val response = client.newCall(builder.build()).awaitResponse()
        return response.use {
            withContext(ioDispatcher) {
                if (!it.isSuccessful) throw IOException("Call provider returned HTTP ${it.code}")
                val source = it.body.source()
                require(!source.request(MAX_CALL_DESCRIPTION_BYTES + 1)) { "Call description is too large" }
                val description = try {
                    kotlinJsonMapper.decodeFromString<NativeCallDescription>(source.readUtf8())
                } catch (_: SerializationException) {
                    // Decoder messages may contain private response content.
                    throw IOException("Invalid call description")
                }
                require(description.id == invitation.callId) { "Call identity changed" }
                description
            }
        }
    }

    /** Open a bounded PCM session without handing credentials to the WebView. */
    suspend fun openMedia(serverId: Int, path: String, listener: WebSocketListener): WebSocket {
        val base = authorizedRequest(serverId, path).build()
        val url = base.url.newBuilder().setQueryParameter("client_id", mediaClientId).build()
        val request = base.newBuilder().url(url).build()
        return clientProvider().newBuilder().followRedirects(false).followSslRedirects(false)
            .pingInterval(SOCKET_PING_SECONDS, TimeUnit.SECONDS).build().newWebSocket(request, listener)
    }

    private suspend fun authorizedRequest(serverId: Int, path: String): Request.Builder {
        requireNotNull(serverManager.getServer(serverId)) { "Unknown call server" }
        val connection = serverManager.connectionStateProvider(serverId)
        val base = requireNotNull(connection.urlFlow().firstUrlOrNull()) {
            "No permitted server URL"
        }.toString().toHttpUrl()
        val url = nativeCallUrl(base, path)
        require(connection.canSafelySendCredentials(url.toString())) { "Unsafe call connection" }
        val token = try {
            serverManager.authenticationRepository(serverId).buildBearerToken()
        } catch (_: AuthorizationException) {
            // Authentication errors can contain private server response bodies.
            throw IOException("Unable to authenticate the call provider")
        }
        return Request.Builder().url(url).header("Authorization", token)
    }
}

private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { _, value, _ -> value.close() }
        }
    })
}
