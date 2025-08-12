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

    abstract suspend fun getPendingDaoEntity(): T

    abstract val widgetClass: Class<*>

    @RequiresApi(Build.VERSION_CODES.O)
    protected suspend fun createWidget() {
        try {
            val pendingEntity = getPendingDaoEntity()
            Timber.e("Pending entity = $pendingEntity")
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
                        PendingIntent.FLAG_MUTABLE,
                    ),
                )
            }.first()
            finish()
        } catch (e: IllegalStateException) {
            Timber.e(e, "When creating widget")
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
            intent.action = BaseWidgetProvider.RECEIVE_DATA
            context.sendBroadcast(intent)

            // Make sure we pass back the original appWidgetId
            setResult(
                RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
            )
            finish()
        } catch (e: IllegalStateException) {
            Timber.e(e, "When updating widget $appWidgetId")
            showAddWidgetError()
        }
    }
}
