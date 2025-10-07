package io.homeassistant.companion.android.data.wear

import androidx.annotation.VisibleForTesting
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.WearableListenerService
import io.homeassistant.companion.android.common.util.WearDataMessages.DnsLookup.PATH_DNS_LOOKUP
import io.homeassistant.companion.android.common.util.WearDataMessages.DnsLookup.decodeDNSRequest
import io.homeassistant.companion.android.common.util.WearDataMessages.DnsLookup.encodeDNSResult
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

class WearDnsRequestListener @VisibleForTesting constructor(
    private val dns: Dns = Dns.SYSTEM,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job()),
) : WearableListenerService() {

    override fun onRequest(nodeId: String, path: String, request: ByteArray): Task<ByteArray>? {
        if (path == PATH_DNS_LOOKUP) {
            return scope.async { dnsRequest(request) }.asTask()
        } else {
            Timber.w("Received a path ($path) that is not supported by this listener. Check the manifest intent-filter")
            return null
        }
    }

    private suspend fun dnsRequest(request: ByteArray): ByteArray = withContext(Dispatchers.IO) {
        val hostname = request.decodeDNSRequest()
        try {
            dns.lookup(hostname).encodeDNSResult()
        } catch (uhe: UnknownHostException) {
            Timber.d(uhe, "UnknownHostException for Wear DNS query: $hostname")
            byteArrayOf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        scope.cancel("WearDataListener.onDestroy")
    }
}
