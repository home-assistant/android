package io.homeassistant.companion.android.fakes

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.Node

class FakeCapabilityClient(context: Context) : CapabilityClient(context, Settings.Builder().build()) {
    val capabilities: MutableMap<String, Set<String>> = mutableMapOf()
    val nodes: MutableMap<String, Set<Node>> = mutableMapOf()

    fun setNodes(capability: String, nodes: Set<String>) {
        this.nodes[capability] = nodes.mapTo(mutableSetOf()) { FakeNode(it, it, true) }
    }

    override fun addListener(listener: OnCapabilityChangedListener, capability: String): Task<Void?> {
        TODO("Not yet implemented")
    }

    override fun addListener(listener: OnCapabilityChangedListener, uri: Uri, filterType: Int): Task<Void?> {
        TODO("Not yet implemented")
    }

    override fun addLocalCapability(capability: String): Task<Void?> {
        TODO("Not yet implemented")
    }

    override fun getAllCapabilities(nodeFilter: Int): Task<Map<String?, CapabilityInfo?>?> {
        TODO("Not yet implemented")
    }

    override fun getCapability(capability: String, nodeFilter: Int): Task<CapabilityInfo?> {
        val nodes =
            nodes[capability] ?: capabilities[capability].orEmpty().mapTo(mutableSetOf()) { FakeNode(it, it, true) }

        return FakeTask(
            Result.success(
                FakeCapabilityInfo(
                    capability,
                    nodes,
                ),
            ),
        )
    }

    override fun removeListener(listener: OnCapabilityChangedListener): Task<Boolean?> {
        TODO("Not yet implemented")
    }

    override fun removeListener(listener: OnCapabilityChangedListener, capability: String): Task<Boolean?> {
        TODO("Not yet implemented")
    }

    override fun removeLocalCapability(capability: String): Task<Void?> {
        TODO("Not yet implemented")
    }
}
