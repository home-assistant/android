package io.homeassistant.companion.android.data

import android.content.Context
import com.google.android.gms.common.api.GoogleApi
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.internal.zzao
import java.net.InetAddress
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow


@Implements(Wearable::class, isInAndroidSdk = false)
class ShadowWearable {
    companion object {
        @Implementation
        @JvmStatic
        fun getCapabilityClient(context: Context): CapabilityClient = FakeCapabilityClient(context)

        @Implementation
        @JvmStatic
        fun getMessageClient(context: Context): MessageClient = FakeMessageClient(context)
    }
}
