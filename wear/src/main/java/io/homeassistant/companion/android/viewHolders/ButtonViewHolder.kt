package io.homeassistant.companion.android.viewHolders

import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import io.homeassistant.companion.android.R

class ButtonViewHolder(val v: View, val onClick: (String) -> Unit) :
    RecyclerView.ViewHolder(v) {

    var txtName: TextView = v.findViewById(R.id.txt_name)

    var color: Int? = null
        set(value) {
            v.background.mutate().setTint(ContextCompat.getColor(v.context, value!!))
            field = value
        }

    var id: String = ""

    init {
        v.setOnClickListener {
            onClick(id)
        }
    }
}
