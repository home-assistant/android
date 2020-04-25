package io.homeassistant.companion.android.wear.actions

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.homeassistant.companion.android.common.actions.WearAction
import io.homeassistant.companion.android.wear.R
import io.homeassistant.companion.android.wear.databinding.ItemActionBinding
import io.homeassistant.companion.android.wear.util.resources.actionIconById

class ActionsAdapter(
    private val onClick: (WearAction) -> Unit,
    private val onLongClick: (WearAction) -> Unit
) : ListAdapter<WearAction, ActionsAdapter.ViewHolder>(
    AsyncDifferConfig.Builder(WearActionListDiffer()).build()
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemActionBinding.inflate(inflater, parent, false)
        val click = { index: Int -> onClick(getItem(index)) }
        val longClick = { index: Int -> onLongClick(getItem(index)) }
        return ViewHolder(binding, click, longClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val action = getItem(position) ?: return
        val binding = ItemActionBinding.bind(holder.itemView)
        binding.icon.setImageResource(actionIconById(action.icon))
        binding.name.text = action.name
    }

    class ViewHolder(
        binding: ItemActionBinding,
        private val onClick: (Int) -> Unit,
        private val onLongClick: (Int) -> Unit
    ) : RecyclerView.ViewHolder(binding.root), View.OnClickListener, View.OnLongClickListener {

        init {
            itemView.setOnClickListener(this)
            itemView.setOnLongClickListener(this)
        }

        override fun onClick(v: View) = onClick(adapterPosition)
        override fun onLongClick(v: View?): Boolean = onLongClick(adapterPosition).run { true }
    }

    private class WearActionListDiffer :  DiffUtil.ItemCallback<WearAction>() {
        override fun areItemsTheSame(oldItem: WearAction, newItem: WearAction): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: WearAction, newItem: WearAction): Boolean {
            return oldItem == newItem
        }
    }

}