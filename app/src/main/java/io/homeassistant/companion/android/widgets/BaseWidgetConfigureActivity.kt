package io.homeassistant.companion.android.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.database.widget.WidgetDao
import kotlinx.coroutines.launch

abstract class BaseWidgetConfigureActivity : BaseActivity() {

    protected var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    abstract val dao: WidgetDao

    protected val onDeleteWidget = View.OnClickListener { deleteConfirmation(it.context) }

    private fun deleteConfirmation(context: Context) {
        val builder: android.app.AlertDialog.Builder = android.app.AlertDialog.Builder(context)

        builder.setTitle(R.string.confirm_delete_this_widget_title)
        builder.setMessage(R.string.confirm_delete_this_widget_message)

        builder.setPositiveButton(
            R.string.confirm_positive
        ) { dialog, _ ->
            lifecycleScope.launch {
                dao.delete(appWidgetId)
                dialog.dismiss()
                finish()
            }
        }

        builder.setNegativeButton(
            R.string.confirm_negative
        ) { dialog, _ -> // Do nothing
            dialog.dismiss()
        }

        val alert: android.app.AlertDialog? = builder.create()
        alert?.show()
    }

    protected fun showAddWidgetError() {
        Toast.makeText(applicationContext, R.string.widget_creation_error, Toast.LENGTH_LONG).show()
    }
}
