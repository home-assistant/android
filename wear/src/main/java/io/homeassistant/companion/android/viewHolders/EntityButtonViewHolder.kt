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

            // Set default icon
            if (value.entityId.split(".")[0] == "script") {
                imgIcon.setImageResource(R.drawable.ic_scripts)
            } else if (value.entityId.split(".")[0] == "light") {
                imgIcon.setImageResource(R.drawable.ic_light)
            } else if (value.entityId.split(".")[0] == "cover") {
                imgIcon.setImageResource(R.drawable.ic_garage)
            } else {
                imgIcon.setImageResource(R.drawable.ic_scenes)
            }

            /*if (entityAttributes.containsKey("icon")) {
                Need to implement dynamic icon loading here
                The default library used (com.maltaisn:icondialog) does not allow to get icons by the mdi: string
                Alternative library: https://github.com/outadoc/mdi-android
                This one requires a github access token...
            }*/
            field = value
        }

    init {
        v.setOnClickListener {
            entity?.let { onClick(it) }
        }
    }
}
