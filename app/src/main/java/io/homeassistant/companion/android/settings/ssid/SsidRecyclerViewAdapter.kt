package io.homeassistant.companion.android.settings.ssid

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.homeassistant.companion.android.databinding.ItemSsidBinding
import io.homeassistant.companion.android.util.recyclerview.GenericMapperDiffCallback

class SsidRecyclerViewAdapter : ListAdapter<String, SsidRecyclerViewAdapter.SsidBindingViewHolder>(
    AsyncDifferConfig.Builder(GenericMapperDiffCallback<String> { element -> element }).build()
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SsidBindingViewHolder {
        val binding = ItemSsidBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SsidBindingViewHolder(binding) { adapterPosition ->
            val ssids = currentList.filterIndexed { index, _ -> adapterPosition != index }
            submitList(ssids)
        }
    }

    override fun onBindViewHolder(holder: SsidBindingViewHolder, position: Int) {
        val ssidEntry = getItem(position)
        val binding = ItemSsidBinding.bind(holder.itemView)
        binding.ssidText.text = ssidEntry
    }

    fun submitSet(set: Set<String>) = submitList(set.sorted())

    class SsidBindingViewHolder(
        binding: ItemSsidBinding,
        private val clickListener: (Int) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.actionRemove.setOnClickListener { clickListener(adapterPosition) }
        }
    }
}
