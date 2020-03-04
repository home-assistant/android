package io.homeassistant.companion.android.util.recyclerview

import android.annotation.SuppressLint
import androidx.recyclerview.widget.DiffUtil

class GenericMapperDiffCallback<T>(private val mapper: (T) -> Any?) : DiffUtil.ItemCallback<T>() {

    override fun areItemsTheSame(oldItem: T, newItem: T): Boolean = mapper(oldItem) == mapper(newItem)

    @SuppressLint("DiffUtilEquals")
    override fun areContentsTheSame(oldItem: T, newItem: T): Boolean = oldItem == newItem
}
