package io.homeassistant.companion.android.viewHolders

import android.graphics.Color
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
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

            if (entityAttributes.containsKey("icon")) {
                val icon: String = entityAttributes["icon"]!!.split(":")[1]
                val iconDrawable = IconicsDrawable(imgIcon.context, "cmd-$icon").apply {
                    colorInt = Color.WHITE
                    sizeDp = 24
                }
                imgIcon.setImageDrawable(iconDrawable)
            } else {
                // Set default icon
                when {
                    value.entityId.split(".")[0] == "script" -> {
                        imgIcon.setImageResource(R.drawable.ic_scripts)
                    }
                    value.entityId.split(".")[0] == "light" -> {
                        imgIcon.setImageResource(R.drawable.ic_light)
                    }
                    value.entityId.split(".")[0] == "cover" -> {
                        imgIcon.setImageResource(R.drawable.ic_garage)
                    }
                    else -> {
                        imgIcon.setImageResource(R.drawable.ic_scenes)
                    }
                }
            }

            field = value
        }

    init {
        v.setOnClickListener {
            entity?.let { onClick(it) }
        }
    }
}
