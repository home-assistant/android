package io.homeassistant.companion.android.fakes

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient

class FakeNodeClient(context: Context) : NodeClient(context, Settings.Builder().build()) {

    private var nodes = emptyList<String>()

    fun setNodes(nodes: List<String>) {
        this.nodes = nodes
    }

    override fun getCompanionPackageForNode(p0: String): Task<String?> {
        TODO("Not yet implemented")
    }

    override fun getConnectedNodes(): Task<List<Node?>?> {
        return FakeTask(Result.success(nodes.mapTo(mutableListOf()) { FakeNode(it, it, true) }))
    }

    override fun getLocalNode(): Task<Node?> {
        TODO("Not yet implemented")
    }

    override fun getNodeId(p0: String): Task<String?> {
        TODO("Not yet implemented")
    }
}
