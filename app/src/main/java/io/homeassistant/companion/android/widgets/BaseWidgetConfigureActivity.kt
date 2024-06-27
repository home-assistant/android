package io.homeassistant.companion.android.widgets

import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.core.view.isVisible

abstract class BaseWidgetConfigureActivity : BaseWidgetConfigureNoUiActivity() {

    abstract val serverSelect: View
    abstract val serverSelectList: Spinner

    protected fun setupServerSelect(widgetServerId: Int?) {
        onServerIdProvided(widgetServerId)
    }

    override fun onServerPickerDataReceived(serverNames: List<String>, activeServerPosition: Int) {
        serverSelectList.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, serverNames)
        serverSelectList.setSelection(activeServerPosition)

        serverSelectList.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectItem(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                clearSelection()
            }
        }
    }

    override fun onServerChanged(serverId: Int) {
        onServerSelected(serverId)
    }

    override fun onServerPickerVisible(isShow: Boolean) {
        serverSelect.isVisible = isShow
    }

    /**
     * @deprecated Use [onServerChanged] instead
     */
    abstract fun onServerSelected(serverId: Int)
}
