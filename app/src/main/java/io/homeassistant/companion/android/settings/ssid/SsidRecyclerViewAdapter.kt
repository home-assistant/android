package io.homeassistant.companion.android.settings.ssid

import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.databinding.ItemSsidBinding
import io.homeassistant.companion.android.util.recyclerview.GenericMapperDiffCallback
import io.homeassistant.companion.android.common.R as commonR

class SsidRecyclerViewAdapter :
    ListAdapter<SsidRecyclerViewAdapter.SsidEntry, SsidRecyclerViewAdapter.SsidBindingViewHolder>(
        AsyncDifferConfig.Builder(GenericMapperDiffCallback<SsidEntry> { element -> element })
            .build()
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
        try {
            val unwrappedDrawable = AppCompatResources.getDrawable(binding.ssidIcon.context, R.drawable.ic_wifi)
            if (ssidEntry.connected) {
                unwrappedDrawable?.setTint(ContextCompat.getColor(binding.ssidIcon.context, commonR.color.colorAccent))
            } else {
                unwrappedDrawable?.setTint(Color.DKGRAY)
            }
            binding.ssidIcon.setImageDrawable(unwrappedDrawable)
        } catch (e: Exception) {
            Log.e("SsidRecyclerViewAdapter", "Unable to set the icon tint", e)
        }
        binding.ssidText.text = ssidEntry.name
    }

    fun submitSet(set: Set<SsidEntry>) = submitList(set.sortedBy { it.name })

    class SsidBindingViewHolder(
        binding: ItemSsidBinding,
        private val clickListener: (Int) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.actionRemove.setOnClickListener { clickListener(absoluteAdapterPosition) }
        }
    }

    data class SsidEntry(val name: String, val connected: Boolean)
}
