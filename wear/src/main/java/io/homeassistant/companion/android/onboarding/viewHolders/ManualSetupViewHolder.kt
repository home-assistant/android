package io.homeassistant.companion.android.onboarding.viewHolders

import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.homeassistant.companion.android.R

class ManualSetupViewHolder(v: View, val onClick: () -> Unit) :
    RecyclerView.ViewHolder(v) {

    val text: TextView = v.findViewById(R.id.name)

    init {
        // Increase margin
        val marginTop = (20 * Resources.getSystem().displayMetrics.density).toInt()
        (v.layoutParams as ViewGroup.MarginLayoutParams).topMargin = marginTop

        // Set onclick listener
        v.setOnClickListener {
            onClick()
        }
    }
}
