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

class HomeListAdapter() : RecyclerView.Adapter<ViewHolder>() {

    lateinit var onSceneClicked: (Entity<Any>) -> Unit
    lateinit var onButtonClicked: (String) -> Unit

    val scenes = arrayListOf<Entity<Any>>()
    val scripts = arrayListOf<Entity<Any>>()

    companion object {
        private const val TYPE_SCENE = 1 // Used for scenes and scripts
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
        if (holder is EntityButtonViewHolder) {
            if (position < scenes.size + 1)
                holder.entity = scenes[position - 1]
            else
                holder.entity = scripts[position - 2 - scenes.size]
        } else if (holder is HeaderViewHolder) {
            when (position) {
                0 -> holder.headerTextView.setText(R.string.scenes)
                scenes.size + 1 -> holder.headerTextView.setText(R.string.scripts)
                else -> holder.headerTextView.setText(R.string.other)
            }
        } else if (holder is ButtonViewHolder) {
            holder.txtName.setText(R.string.logout)
            holder.id = BUTTON_ID_LOGOUT
            holder.color = R.color.colorWarning
        }
    }

    override fun getItemCount() = max(scenes.size + scripts.size + 4, 6)

    override fun getItemViewType(position: Int): Int {
        /*
        Current layout contains of three sections:
        # Scenes
        - scene 1
        - scene 2
        - etc
        # Scripts
        - script 1
        - script 2
        - etc
        # Other
        - Logout

        Envisioned final layout:
        # Scenes
        - 3 favorite scenes
        - More scenes button
        # Devices
        - 3 favorite devices
        - More devices button
        # Scripts
        - 3 favorite scripts
        - More scripts button
        # Other
        - Settings
         */
        return when {
            position == 0 || position == scenes.size + 1 || position == itemCount - 2 -> TYPE_HEADER
            position == itemCount - 1 -> TYPE_BUTTON
            position < scenes.size + 1 && scenes.size > 0 -> TYPE_SCENE
            position > scenes.size + 1 && scripts.size > 0 -> TYPE_SCENE
            else -> TYPE_LOADING
        }
    }
}
