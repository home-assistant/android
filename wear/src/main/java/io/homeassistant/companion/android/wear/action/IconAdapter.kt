package io.homeassistant.companion.android.wear.action

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import io.homeassistant.companion.android.wear.databinding.ItemIconBinding
import io.homeassistant.companion.android.wear.util.resources.actionIconById
import io.homeassistant.companion.android.wear.util.resources.actionIconCount

class IconAdapter(context: Context) : ArrayAdapter<Int>(context, 0) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val binding = if (convertView != null) ItemIconBinding.bind(convertView)
        else ItemIconBinding.inflate(LayoutInflater.from(parent.context), parent, false)

        binding.icon.setImageResource(actionIconById(position))

        return binding.root
    }

    override fun getCount(): Int = actionIconCount

}