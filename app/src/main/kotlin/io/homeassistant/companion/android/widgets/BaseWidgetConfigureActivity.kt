package io.homeassistant.companion.android.widgets

import android.app.Activity
import android.app.ActivityOptions
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.annotation.RequiresApi
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.widget.WidgetDao
import javax.inject.Inject

abstract class BaseWidgetConfigureActivity : BaseActivity() {

    @Inject
    lateinit var serverManager: ServerManager

    protected var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    abstract val dao: WidgetDao

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

/**
 * Creates a [PendingIntent] used as a successful callback when a user sets up a widget as pinned.
 * This intent will be sent to the Activity that was used to handle the callback.
 *
 * It complies with the start background activities requirements from
 * https://developer.android.com/guide/components/activities/background-starts#creator-pendingintent
 *
 * @param callbackKeyName The name of the extra to be put in the intent, indicating the callback key.
 * @return A PendingIntent to use as a callback in successCallback of [androidx.glance.appwidget.GlanceAppWidgetManager.requestPinGlanceAppWidget]
 * and [android.appwidget.AppWidgetManager.requestPinAppWidget].
 */
@RequiresApi(Build.VERSION_CODES.O)
fun Activity.getPinCallbackPendingIntent(
    callbackKeyName: String
): PendingIntent {
    val options = ActivityOptions.makeBasic()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
        options.pendingIntentCreatorBackgroundActivityStartMode = ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        options.pendingIntentCreatorBackgroundActivityStartMode = ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
    }

    return PendingIntent.getActivity(
        this,
        System.currentTimeMillis().toInt(),
        Intent(this, this::class.java)
            .putExtra(callbackKeyName, true)
            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        options.toBundle(),
    )
}
