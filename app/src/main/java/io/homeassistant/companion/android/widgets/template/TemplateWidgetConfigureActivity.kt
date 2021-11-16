package io.homeassistant.companion.android.widgets.template

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Html.fromHtml
import android.view.View
import android.view.View.VISIBLE
import androidx.core.widget.doAfterTextChanged
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.databinding.WidgetTemplateConfigureBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TemplateWidgetConfigureActivity : BaseActivity() {
    companion object {
        private const val TAG: String = "TemplateWidgetConfigAct"
    }

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    private lateinit var binding: WidgetTemplateConfigureBinding

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    public override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)

        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)

        binding = WidgetTemplateConfigureBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

        val templateWidgetDao = AppDatabase.getInstance(applicationContext).templateWidgetDao()
        val templateWidget = templateWidgetDao.get(appWidgetId)
        if (templateWidget != null) {
            binding.templateText.setText(templateWidget.template)
            binding.addButton.setText(R.string.update_widget)
            if (templateWidget.template.isNotEmpty())
                renderTemplateText(templateWidget.template)
            else {
                binding.renderedTemplate.text = getString(R.string.empty_template)
                binding.addButton.isEnabled = false
            }
            binding.deleteButton.visibility = VISIBLE
            binding.deleteButton.setOnClickListener(onDeleteWidget)
        }

        binding.templateText.doAfterTextChanged { editableText ->
            if (editableText == null)
                return@doAfterTextChanged
            if (editableText.isNotEmpty()) {
                binding.addButton.isEnabled = true
                renderTemplateText(editableText.toString())
            } else {
                binding.renderedTemplate.text = getString(R.string.empty_template)
                binding.addButton.isEnabled = false
            }
        }

        binding.addButton.setOnClickListener {
            val createIntent = Intent().apply {
                action = TemplateWidget.RECEIVE_DATA
                component = ComponentName(applicationContext, TemplateWidget::class.java)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(TemplateWidget.EXTRA_TEMPLATE, binding.templateText.text.toString())
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
                binding.renderedTemplate.text = fromHtml(templateText)
                binding.addButton.isEnabled = enabled
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
