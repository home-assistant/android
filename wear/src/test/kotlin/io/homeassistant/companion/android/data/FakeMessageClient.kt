package io.homeassistant.companion.android.data

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageClient

class FakeMessageClient(context: Context): MessageClient(context, Settings.Builder().build()) {
    override fun addListener(p0: OnMessageReceivedListener): Task<Void?> {
        TODO("Not yet implemented")
    }

    override fun addListener(p0: OnMessageReceivedListener, p1: Uri, p2: Int): Task<Void?> {
        TODO("Not yet implemented")
    }

    override fun addRpcService(p0: RpcService, p1: String): Task<Void?> {
        TODO("Not yet implemented")
    }

    override fun addRpcService(p0: RpcService, p1: String, p2: String): Task<Void?> {
        TODO("Not yet implemented")
    }

    override fun removeListener(p0: OnMessageReceivedListener): Task<Boolean?> {
        TODO("Not yet implemented")
    }

    override fun removeRpcService(p0: RpcService): Task<Boolean?> {
        TODO("Not yet implemented")
    }

    override fun sendMessage(p0: String, p1: String, p2: ByteArray?): Task<Int?> {
        TODO("Not yet implemented")
    }

    override fun sendRequest(p0: String, p1: String, p2: ByteArray?): Task<ByteArray?> {
        TODO("Not yet implemented")
    }

}