package io.homeassistant.companion.android.data.wear

import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.util.toHexString
import java.net.UnknownHostException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.tasks.asTask
import kotlinx.coroutines.withContext
import okhttp3.Dns
import timber.log.Timber

@AndroidEntryPoint
class WearDnsRequestListener : WearableListenerService() {

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onRequest(nodeId: String, path: String, request: ByteArray): Task<ByteArray>? {
        if (path == "/network/dnsLookup") {
            return mainScope.async { dnsRequest(request) }.asTask()
        } else {
            Timber.w("Received a path ($path) that is not supported by this listener. Check the manifest intent-filter")
            return null
        }
    }

    private suspend fun dnsRequest(request: ByteArray): ByteArray = withContext(Dispatchers.IO) {
        val hostname = String(request, Charsets.UTF_8)
        try {
            Dns.SYSTEM.lookup(hostname).joinToString(",") { it.address.toHexString() }.encodeToByteArray()
        } catch (uhe: UnknownHostException) {
            byteArrayOf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        mainScope.cancel("WearDataListener.onDestroy")
    }
}
