package io.homeassistant.companion.android.viewHolders

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.integration.Entity

class EntityButtonViewHolder(v: View, val onClick: (Entity<Any>) -> Unit) :
    RecyclerView.ViewHolder(v) {

    private var txtName: TextView = v.findViewById(R.id.txt_name)
    private var imgIcon: ImageView = v.findViewById(R.id.img_icon)

    var entity: Entity<Any>? = null
        set(value) {
            val entityAttributes = value?.attributes as Map<String, String>
            txtName.text = entityAttributes["friendly_name"]
            //TODO set icon
            field = value
        }

    init {
        v.setOnClickListener {
            entity?.let { onClick(it) }
        }
    }
}
