package io.homeassistant.companion.android.onboarding

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.onboarding.viewHolders.HeaderViewHolder
import io.homeassistant.companion.android.onboarding.viewHolders.InstanceViewHolder

class ServerListAdapter(
        val servers: ArrayList<HomeAssistantInstance>,
        private val onClick: (HomeAssistantInstance) -> Unit) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_INSTANCE = 1
        private const val TYPE_HEADER = 2
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {
        if (viewType == TYPE_INSTANCE) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.listitem_instance, parent, false)
            return InstanceViewHolder(view, onClick)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.listitem_header, parent, false)
            return HeaderViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is InstanceViewHolder) {
            holder.server = servers[position-1]
            holder.nodeId = 1 // TODO Set with node id from data layer
        } else if (holder is HeaderViewHolder) {
            holder.headerTextView.setText(R.string.list_header_instances)
        }
    }

    override fun getItemCount() = servers.size+1

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) {
            TYPE_HEADER
        } else {
            TYPE_INSTANCE
        }
    }

}