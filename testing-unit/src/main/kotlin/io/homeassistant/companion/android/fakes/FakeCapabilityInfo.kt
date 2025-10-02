package io.homeassistant.companion.android.fakes

import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.Node

class FakeCapabilityInfo(
    @JvmField
    val name: String,
    @JvmField
    val nodes: Set<Node>,
) : CapabilityInfo {
    override fun getName(): String = name

    override fun getNodes(): Set<Node?> = nodes
}
