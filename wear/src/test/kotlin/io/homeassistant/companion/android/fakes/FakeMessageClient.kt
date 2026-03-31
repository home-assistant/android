package io.homeassistant.companion.android.fakes

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.MessageClient

class FakeMessageClient(context: Context) : MessageClient(context, Settings.Builder().build()) {
    var onRequest: (ByteArray) -> ByteArray = { throw UnsupportedOperationException() }

    override fun addListener(listener: OnMessageReceivedListener): Task<Void?> {
        TODO("Not yet implemented")
    }

    override fun addListener(listener: OnMessageReceivedListener, path: Uri, filterType: Int): Task<Void?> {
        TODO("Not yet implemented")
    }

    override fun addRpcService(service: RpcService, pathPrefix: String): Task<Void?> {
        TODO("Not yet implemented")
    }

    override fun addRpcService(service: RpcService, pathPrefix: String, nodeId: String): Task<Void?> {
        TODO("Not yet implemented")
    }

    override fun removeListener(listener: OnMessageReceivedListener): Task<Boolean?> {
        TODO("Not yet implemented")
    }

    override fun removeRpcService(listener: RpcService): Task<Boolean?> {
        TODO("Not yet implemented")
    }

    override fun sendMessage(nodeId: String, path: String, data: ByteArray?): Task<Int?> {
        TODO("Not yet implemented")
    }

    override fun sendRequest(nodeId: String, path: String, data: ByteArray?): Task<ByteArray?> {
        val result = runCatching<ByteArray?> { onRequest(data!!) }

        return FakeTask(result)
    }
}
