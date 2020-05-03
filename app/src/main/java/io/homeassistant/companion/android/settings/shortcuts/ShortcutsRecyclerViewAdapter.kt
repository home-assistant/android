package io.homeassistant.companion.android.settings.shortcuts

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.homeassistant.companion.android.databinding.ItemShortcutBinding
import io.homeassistant.companion.android.domain.integration.Panel

class ShortcutsRecyclerViewAdapter(
    private val panels: List<Panel>,
    val context: Context,
    private val onCreateShortcut: (Panel) -> Unit
) :
    RecyclerView.Adapter<ShortcutsRecyclerViewAdapter.ShortcutBindingViewHolder>() {

    override fun getItemCount(): Int {
        return panels.size
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ShortcutBindingViewHolder {
        val binding =
            ItemShortcutBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ShortcutBindingViewHolder(binding) { onCreateShortcut(panels[it]) }
    }

    override fun onBindViewHolder(holder: ShortcutBindingViewHolder, position: Int) {
        val panel = panels[position]
        val binding = ItemShortcutBinding.bind(holder.itemView)
        binding.panelText.text = panel.title_localized
    }

    class ShortcutBindingViewHolder(
        binding: ItemShortcutBinding,
        private val clickListener: (Int) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.actionAdd.setOnClickListener { clickListener(adapterPosition) }
        }
    }
}
