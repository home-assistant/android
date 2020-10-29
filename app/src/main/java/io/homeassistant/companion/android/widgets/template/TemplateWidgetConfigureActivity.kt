package io.homeassistant.companion.android.widgets.template

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.View.VISIBLE
import androidx.core.widget.doAfterTextChanged
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.widgets.DaggerProviderComponent
import javax.inject.Inject
import kotlinx.android.synthetic.main.widget_template_configure.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TemplateWidgetConfigureActivity : BaseActivity() {
    companion object {
        private const val TAG: String = "TemplateWidgetConfigAct"
    }

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    public override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)

        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)

        setContentView(R.layout.widget_template_configure)

        // Find the widget id from the intent.
        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        // If this activity was started with an intent without an app widget ID, finish with an error.
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        // Inject components
        DaggerProviderComponent
            .builder()
            .appComponent((application as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)

        val templateWidgetDao = AppDatabase.getInstance(applicationContext).templateWidgetDao()
        val templateWidget = templateWidgetDao.get(appWidgetId)
        if (templateWidget != null) {
            templateText.setText(templateWidget.template)
            add_button.setText(R.string.update_widget)
            renderTemplateText(templateWidget.template)
            delete_button.visibility = VISIBLE
            delete_button.setOnClickListener(onDeleteWidget)
        }

        templateText.doAfterTextChanged { editableText ->
            if (editableText == null)
                return@doAfterTextChanged
            renderTemplateText(editableText.toString())
        }

        add_button.setOnClickListener {
            val createIntent = Intent().apply {
                action = TemplateWidget.RECEIVE_DATA
                component = ComponentName(applicationContext, TemplateWidget::class.java)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(TemplateWidget.EXTRA_TEMPLATE, templateText.text.toString())
            }
            applicationContext.sendBroadcast(createIntent)

            setResult(
                RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            )
            finish()
        }
    }

    override fun onDestroy() {
        mainScope.cancel()
        super.onDestroy()
    }

    private fun renderTemplateText(template: String) {
        ioScope.launch {
            var templateText: String?
            var enabled: Boolean
            try {
                templateText = integrationUseCase.renderTemplate(template, mapOf())
                enabled = true
            } catch (e: Exception) {
                templateText = "Error in template"
                enabled = false
            }
            runOnUiThread {
                renderedTemplate.text = templateText
                add_button.isEnabled = enabled
            }
        }
    }

    private var onDeleteWidget = View.OnClickListener {
        val context = this@TemplateWidgetConfigureActivity
        deleteConfirmation(context)
    }

    private fun deleteConfirmation(context: Context) {
        val templateWidgetDao = AppDatabase.getInstance(context).templateWidgetDao()

        val builder: android.app.AlertDialog.Builder = android.app.AlertDialog.Builder(context)

        builder.setTitle(R.string.confirm_delete_this_widget_title)
        builder.setMessage(R.string.confirm_delete_this_widget_message)

        builder.setPositiveButton(
            R.string.confirm_positive
        ) { dialog, _ ->
            templateWidgetDao.delete(appWidgetId)
            dialog.dismiss()
            finish()
        }

        builder.setNegativeButton(
            R.string.confirm_negative
        ) { dialog, _ -> // Do nothing
            dialog.dismiss()
        }

        val alert: android.app.AlertDialog? = builder.create()
        alert?.show()
    }
}
