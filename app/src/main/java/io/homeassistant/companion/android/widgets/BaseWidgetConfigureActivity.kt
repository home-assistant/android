package io.homeassistant.companion.android.widgets

import android.appwidget.AppWidgetManager
import android.widget.Toast
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.database.widget.WidgetDao

abstract class BaseWidgetConfigureActivity : BaseActivity() {

    protected var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    abstract val dao: WidgetDao

    protected fun showAddWidgetError() {
        Toast.makeText(applicationContext, R.string.widget_creation_error, Toast.LENGTH_LONG).show()
    }
}
