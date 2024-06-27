package io.homeassistant.companion.android.widgets

import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.widget.Toast
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.widget.WidgetDao
import javax.inject.Inject

abstract class BaseWidgetConfigureNoUiActivity : BaseActivity() {

    @Inject
    lateinit var serverManager: ServerManager

    protected var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    abstract val dao: WidgetDao

    var selectedServerId: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)
    }

    protected fun onServerIdProvided(widgetServerId: Int?) {
        val servers = serverManager.defaultServers
        val activeServerId = serverManager.getServer()?.id
        selectedServerId = widgetServerId ?: activeServerId

        val serverNamesToShow = servers.map { it.friendlyName }
        val activeServerPosition = servers.indexOfFirst { it.id == selectedServerId }
        onServerPickerDataReceived(serverNamesToShow, activeServerPosition)

        val showServerPicker = serverManager.defaultServers.size > 1 || serverManager.isInvalidServerId(widgetServerId)
        onServerPickerVisible(isShow = showServerPicker)
    }

    protected fun selectItem(position: Int) {
        val newId = serverManager.defaultServers.getOrNull(position)?.id
        val isDifferent = selectedServerId != newId
        selectedServerId = newId
        if (isDifferent && newId != null) {
            onServerChanged(newId)
        }
    }

    protected fun clearSelection() {
        selectedServerId = null
    }

    abstract fun onServerPickerDataReceived(serverNames: List<String>, activeServerPosition: Int)

    abstract fun onServerChanged(serverId: Int)

    abstract fun onServerPickerVisible(isShow: Boolean)

    protected fun isValidServerId() = serverManager.defaultServers.any { it.id == selectedServerId }

    protected fun showAddWidgetError() {
        Toast.makeText(applicationContext, R.string.widget_creation_error, Toast.LENGTH_LONG).show()
    }
}

private fun ServerManager.isInvalidServerId(serverId: Int?): Boolean {
    return serverId != null && getServer(serverId) == null
}
