package io.homeassistant.companion.android.settings.manageinstances

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.homeassistant.companion.android.R

class ManageInstanceAdapter(private val instanceInterface: ManageInstanceAdapter.InstanceInterface) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var instances: List<String> =
        ArrayList()

    init {
        setHasStableIds(true)
    }

    interface InstanceInterface {
        fun onInstanceSelected(urlString: String)
        fun onDeleteInstance(urlString: String)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return InstanceViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.instance_view, parent, false)
        )
    }

    override fun getItemCount() = instances.count()

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        holder as InstanceViewHolder
        holder.bind(instances[position])
        holder.itemView.setOnClickListener {
            instanceInterface.onInstanceSelected(
                holder.itemView.findViewById<TextView>(
                    R.id.instance_name
                ).text.toString()
            )
        }
        holder.itemView.findViewById<Button>(R.id.delete_button).setOnClickListener {
            instanceInterface.onDeleteInstance(
                holder.itemView.findViewById<TextView>(
                    R.id.instance_name
                ).text.toString()
            )
        }
    }

    class InstanceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        fun bind(header: String) {
            itemView.findViewById<TextView>(R.id.instance_name).text = header
        }
    }
}