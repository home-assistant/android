package io.homeassistant.companion.android.wear.action

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.homeassistant.companion.android.wear.databinding.ItemIconBinding
import net.steamcrafted.materialiconlib.MaterialDrawableBuilder

class IconAdapter(
    private val clickListener: (MaterialDrawableBuilder.IconValue) -> Unit
) : RecyclerView.Adapter<IconAdapter.ViewHolder>() {

    private val icons = MaterialDrawableBuilder.IconValue.values()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemIconBinding.inflate(inflater, parent, false)
        return ViewHolder(
            binding.root
        ) { position -> clickListener(icons[position]) }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val binding = ItemIconBinding.bind(holder.itemView)
        val icon = icons[position]
        binding.icon.setIcon(icon)
        binding.name.text = icon.name
    }

    override fun getItemCount(): Int = icons.size

    class ViewHolder(view: View, clickListener: (Int) -> Unit) : RecyclerView.ViewHolder(view) {
        init { itemView.setOnClickListener { clickListener(adapterPosition) } }
    }
}
