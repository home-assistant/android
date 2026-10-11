package io.homeassistant.companion.android.viewHolders

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.homeassistant.companion.android.R

class HeaderViewHolder(v: View) : RecyclerView.ViewHolder(v) {

    val headerTextView: TextView = v.findViewById(R.id.headerTextView)
}
