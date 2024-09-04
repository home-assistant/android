package io.homeassistant.companion.android.widgets

import android.appwidget.AppWidgetManager
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.servers.ServerManager
import javax.inject.Inject

abstract class BaseWidgetConfigureActivity : BaseActivity() {
    @Inject
    lateinit var serverManager: ServerManager

    protected var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    abstract val serverSelect: View
    abstract val serverSelectList: Spinner

    var selectedServerId: Int? = null

    protected fun setupServerSelect(widgetServerId: Int?) {
        val servers = serverManager.defaultServers
        val activeServerId = serverManager.getServer()?.id
        serverSelectList.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, servers.map { it.friendlyName })
        serverSelectList.setSelection(
            if (widgetServerId != null) {
                servers.indexOfFirst { it.id == widgetServerId }
            } else {
                servers.indexOfFirst { it.id == activeServerId }
            }
        )

        if (
            serverManager.defaultServers.size > 1 ||
            (widgetServerId != null && serverManager.getServer(widgetServerId) == null)
        ) {
            serverSelect.visibility = View.VISIBLE
        }

        selectedServerId = widgetServerId ?: serverManager.getServer()?.id
        serverSelectList.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val newId = serverManager.defaultServers.getOrNull(position)?.id
                val isDifferent = selectedServerId != newId
                selectedServerId = newId
                if (isDifferent && newId != null) {
                    onServerSelected(newId)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedServerId = null
            }
        }
    }

    abstract fun onServerSelected(serverId: Int)

    protected fun isValidServerId() = selectedServerId in serverManager.defaultServers.map { it.id }

    protected fun showAddWidgetError() {
        Toast.makeText(applicationContext, R.string.widget_creation_error, Toast.LENGTH_LONG).show()
    }
}
