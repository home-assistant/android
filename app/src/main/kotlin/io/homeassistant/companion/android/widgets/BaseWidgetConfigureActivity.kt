package io.homeassistant.companion.android.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import androidx.lifecycle.lifecycleScope
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.widget.WidgetDao
import io.homeassistant.companion.android.database.widget.WidgetEntity
import javax.inject.Inject
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import timber.log.Timber

abstract class BaseWidgetConfigureActivity<T : WidgetEntity<T>, DAO : WidgetDao<T>> : BaseActivity() {

    protected companion object {
        const val FOR_ENTITY = "for_entity"
    }

    @Inject
    lateinit var serverManager: ServerManager

    protected var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    @Inject
    lateinit var dao: DAO

    abstract val serverSelect: View
    abstract val serverSelectList: Spinner

    var selectedServerId: Int? = null

    protected fun setupServerSelect(widgetServerId: Int?) {
        val servers = serverManager.defaultServers
        lifecycleScope.launch {
            val activeServerId = serverManager.getServer()?.id
            serverSelectList.adapter =
                ArrayAdapter(
                    this@BaseWidgetConfigureActivity,
                    android.R.layout.simple_spinner_dropdown_item,
                    servers.map {
                        it.friendlyName
                    },
                )
            serverSelectList.setSelection(
                if (widgetServerId != null) {
                    servers.indexOfFirst { it.id == widgetServerId }
                } else {
                    servers.indexOfFirst { it.id == activeServerId }
                },
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
    }

    abstract fun onServerSelected(serverId: Int)

    protected fun isValidServerId() = selectedServerId in serverManager.defaultServers.map { it.id }

    protected fun showAddWidgetError() {
        Toast.makeText(applicationContext, R.string.widget_creation_error, Toast.LENGTH_LONG).show()
    }

    /**
     * Return a [WidgetEntity] with the current selection, but without pushing this to the [dao]
     */
    abstract suspend fun getPendingDaoEntity(): T

    /**
     * The class of the widget being configured, this is going to be used when sending the broadcast
     * intent for the creation of the widget.
     */
    abstract val widgetClass: Class<*>

    /**
     * Requests the widget to be created and waits until it has been saved to the DAO.
     *
     * **WARNING**: This function does not handle user cancellation. If a user cancels the widget creation,
     * this function will not return. If this function is called again and the user does not cancel,
     * both calls to the function will return. While this behavior could be avoided,
     * it does not cause issues in the current implementation as returning multiple times has no adverse effects.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    protected suspend fun requestWidgetCreation() {
        try {
            val pendingEntity = getPendingDaoEntity()
            // We drop the first value since we only care about knowing when the widget is actually added
            dao.getWidgetCountFlow().drop(1).onStart {
                val context = this@BaseWidgetConfigureActivity
                getSystemService<AppWidgetManager>()?.requestPinAppWidget(
                    ComponentName(context, widgetClass),
                    null,
                    PendingIntent.getBroadcast(
                        context,
                        System.currentTimeMillis().toInt(),
                        Intent(
                            context,
                            widgetClass,
                        ).apply {
                            action = ACTION_APPWIDGET_CREATED
                            putExtra(EXTRA_WIDGET_ENTITY, pendingEntity)
                        },
                        // We need the PendingIntent to be mutable so the system inject the EXTRA_APPWIDGET_ID of the created widget
                        PendingIntent.FLAG_MUTABLE,
                    ),
                )
            }.first()
            finish()
        } catch (e: IllegalStateException) {
            Timber.e(e, "State error when creating widget")
            showAddWidgetError()
        }
    }

    protected suspend fun updateWidget() {
        val context = this@BaseWidgetConfigureActivity

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            showAddWidgetError()
            return
        }

        try {
            val pendingEntity = getPendingDaoEntity()
            dao.add(pendingEntity)

            val intent = Intent(context, widgetClass)
            intent.action = BaseWidgetProvider.UPDATE_WIDGETS
            context.sendBroadcast(intent)

            // Make sure we pass back the original appWidgetId
            setResult(
                RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
            )
            finish()
        } catch (e: IllegalStateException) {
            Timber.e(e, "State error when updating widget $appWidgetId")
            showAddWidgetError()
        }
    }
}
