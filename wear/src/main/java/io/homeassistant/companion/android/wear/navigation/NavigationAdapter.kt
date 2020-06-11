package io.homeassistant.companion.android.wear.navigation

import android.graphics.drawable.Drawable
import androidx.wear.widget.drawer.WearableNavigationDrawerView

class NavigationAdapter : WearableNavigationDrawerView.WearableNavigationDrawerAdapter() {

    private val pages = arrayListOf<NavigationItem>()

    override fun getItemText(pos: Int): CharSequence = pages[pos].title
    override fun getItemDrawable(pos: Int): Drawable = pages[pos].icon
    override fun getCount(): Int = pages.size

    fun submitPages(newPages: List<NavigationItem>) {
        pages.clear()
        pages.addAll(newPages)
        notifyDataSetChanged()
    }

    fun getPage(index: Int): NavigationItem {
        return pages[index]
    }
}
