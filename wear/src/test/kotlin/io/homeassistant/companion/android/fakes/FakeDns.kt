package io.homeassistant.companion.android.fakes

import java.net.InetAddress
import okhttp3.Dns

class FakeDns : Dns {
    val results = mutableMapOf<String, Result<List<InetAddress>>>()

    override fun lookup(hostname: String): List<InetAddress> {
        return results[hostname]?.getOrThrow() ?: throw UnsupportedOperationException()
    }
}
