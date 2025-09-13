package io.homeassistant.companion.android.shadows

import java.net.InetAddress
import okhttp3.Dns
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow

@Implements(className = "okhttp3.Dns\$Companion\$DnsSystem", isInAndroidSdk = false)
class ShadowDnsSystem {
    val results = mutableMapOf<String, Result<List<InetAddress>>>()

    @Implementation
    protected fun lookup(hostname: String): List<InetAddress> {
        return results[hostname]?.getOrThrow() ?: throw UnsupportedOperationException()
    }

    companion object {
        fun shadowOf(dns: Dns): ShadowDnsSystem {
            return Shadow.extract(dns)
        }
    }
}
