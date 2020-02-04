package io.homeassistant.companion.android.widgets

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.TextView
import java.util.Comparator

class SingleItemArrayAdapter<T>(
    context: Context,
    private val createText: (T?) -> String
) : ArrayAdapter<T>(context, android.R.layout.simple_list_item_1) {

    private var filterItems = ArrayList<T>()

    internal fun sort() {
        val comparator = Comparator { t1: T, t2: T ->
            createText(t1).compareTo(createText(t2))
        }
        super.sort(comparator)
        filterItems.sortWith(comparator)
    }

    override fun addAll(collection: MutableCollection<out T>) {
        super.addAll(collection)
        filterItems.addAll(collection)
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val result = FilterResults()

                constraint?.let {
                    val containSeq = ArrayList<T>()

                    for (i in 0 until filterItems.size) {
                        val item = filterItems[i]
                        if (createText(item).contains(constraint)) {
                            containSeq.add(item!!)
                        }
                    }
                    result.values = containSeq
                    result.count = containSeq.size
                }
                return result
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                results?.let {
                    clear()

                    if (results.count > 0) {
                        @Suppress("UNCHECKED_CAST")
                        (results.values as ArrayList<T>).forEach {
                            add(it)
                        }
                    }

                    notifyDataSetChanged()
                }
            }

            override fun convertResultToString(resultValue: Any?): CharSequence {
                if (resultValue != null) {
                    @Suppress("UNCHECKED_CAST")
                    return createText(resultValue as T)
                } else {
                    return ""
                }
            }
        }
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return getView(position, convertView, parent)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: View.inflate(context, android.R.layout.simple_list_item_1, null)
        val item = getItem(position)
        (view as TextView).text = createText(item)
        return view
    }
}
