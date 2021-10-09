package io.homeassistant.companion.android.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.viewHolders.ButtonViewHolder
import io.homeassistant.companion.android.viewHolders.EntityButtonViewHolder
import io.homeassistant.companion.android.viewHolders.HeaderViewHolder
import io.homeassistant.companion.android.viewHolders.LoadingViewHolder
import kotlin.math.max

class HomeListAdapter(
    val scenes: ArrayList<Entity<Any>>
) : RecyclerView.Adapter<ViewHolder>() {

    lateinit var onSceneClicked: (Entity<Any>) -> Unit
    lateinit var onButtonClicked: (String) -> Unit

    companion object {
        private const val TYPE_SCENE = 1
        private const val TYPE_HEADER = 2
        private const val TYPE_LOADING = 3
        private const val TYPE_BUTTON = 4

        const val BUTTON_ID_LOGOUT: String = "logout"
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        return when (viewType) {
            TYPE_SCENE -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.listitem_entity_button, parent, false)
                EntityButtonViewHolder(view, onSceneClicked)
            }
            TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.listitem_header, parent, false)
                HeaderViewHolder(view)
            }
            TYPE_BUTTON -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.listitem_button, parent, false)
                ButtonViewHolder(view, onButtonClicked)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.listitem_loading, parent, false)
                LoadingViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (holder is EntityButtonViewHolder && position <= scenes.size) {
            holder.entity = scenes[position - 1]
        } else if (holder is HeaderViewHolder) {
            if (position == 0) {
                holder.headerTextView.setText(R.string.scenes)
            } else {
                holder.headerTextView.setText(R.string.other)
            }
        } else if (holder is ButtonViewHolder) {
            holder.txtName.setText(R.string.logout)
            holder.id = BUTTON_ID_LOGOUT
            holder.color = R.color.colorWarning
        }
    }

    override fun getItemCount() = max(scenes.size + 3, 4)

    override fun getItemViewType(position: Int): Int {
        return when {
            position == 0 || position == itemCount - 2 -> TYPE_HEADER
            position == itemCount-1 -> TYPE_BUTTON
            scenes.size > 0 -> TYPE_SCENE
            else -> TYPE_LOADING
        }
    }
}
