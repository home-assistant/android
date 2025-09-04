package io.homeassistant.companion.android.data

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo

class FakeCapabilityClient(context: Context): CapabilityClient(context, Settings.Builder().build()) {
    override fun addListener(p0: OnCapabilityChangedListener, p1: String): Task<Void?> {
        TODO("Not yet implemented")
    }

    override fun addListener(p0: OnCapabilityChangedListener, p1: Uri, p2: Int): Task<Void?> {
        TODO("Not yet implemented")
    }

    override fun addLocalCapability(p0: String): Task<Void?> {
        TODO("Not yet implemented")
    }

    override fun getAllCapabilities(p0: Int): Task<Map<String?, CapabilityInfo?>?> {
        TODO("Not yet implemented")
    }

    override fun getCapability(p0: String, p1: Int): Task<CapabilityInfo?> {
        TODO("Not yet implemented")
    }

    override fun removeListener(p0: OnCapabilityChangedListener): Task<Boolean?> {
        TODO("Not yet implemented")
    }

    override fun removeListener(p0: OnCapabilityChangedListener, p1: String): Task<Boolean?> {
        TODO("Not yet implemented")
    }

    override fun removeLocalCapability(p0: String): Task<Void?> {
        TODO("Not yet implemented")
    }
}