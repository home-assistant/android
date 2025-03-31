package io.homeassistant.companion.android.data.wear

import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.tasks.asTask
import okhttp3.Dns

@AndroidEntryPoint
class WearDataListener : WearableListenerService() {

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onRequest(nodeId: String, path: String, request: ByteArray): Task<ByteArray>? {
        if (path == "/dnsLookup") {
            val hostname = String(request, Charsets.UTF_8)
            return mainScope.async(Dispatchers.IO) {
                Dns.SYSTEM.lookup(hostname).first().address
            }.asTask()
        } else {
            Timber.w("Received a path ($path) that is not supported by this listener. Check the manifest intent-filter")
            return null
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        mainScope.cancel("WearDataListener.onDestroy")
    }
}
